/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.opensource.classpath;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.graph.Traverser;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantInterfaceMethodref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.InnerClass;
import org.apache.bcel.classfile.InnerClasses;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.CPInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.ClassPathRepository;
import org.apache.bcel.util.Repository;

/**
 * Class to read symbol references in Java class files and to verify the availability of references
 * in them, through the input class path for a linkage check.
 */
class ClassDumper {

  private final ImmutableList<Path> inputClassPath;
  private final Repository classRepository;
  private final ClassLoader extensionClassLoader;
  private final ImmutableSetMultimap<Path, String> jarFileToClasses;
  private final ImmutableListMultimap<String, Path> classToJarFiles;

  private static Repository createClassRepository(List<Path> paths) {
    ClassPath classPath = new LinkageCheckClassPath(paths);
    return new ClassPathRepository(classPath);
  }

  static ClassDumper create(List<Path> jarPaths) throws IOException {
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
    ClassLoader extensionClassLoader = systemClassLoader.getParent();

    ImmutableList<Path> unreadableFiles =
        jarPaths.stream()
            .filter(jar -> !Files.isRegularFile(jar) || !Files.isReadable(jar))
            .collect(toImmutableList());
    checkArgument(
        unreadableFiles.isEmpty(), "Some jar files are not readable: %s", unreadableFiles);

    return new ClassDumper(jarPaths, extensionClassLoader, mapJarToClasses(jarPaths));
  }

  private ClassDumper(
      List<Path> inputClassPath,
      ClassLoader extensionClassLoader,
      ImmutableSetMultimap<Path, String> jarToClasses) {
    this.inputClassPath = ImmutableList.copyOf(inputClassPath);
    this.classRepository = createClassRepository(inputClassPath);
    this.extensionClassLoader = extensionClassLoader;
    this.jarFileToClasses = ImmutableSetMultimap.copyOf(jarToClasses);
    this.classToJarFiles = ImmutableListMultimap.copyOf(jarToClasses.inverse());
  }

  /**
   * Returns {@link JavaClass} for {@code className} in the input class path using the BCEL API.
   *
   * @see <a href="https://commons.apache.org/proper/commons-bcel/manual/bcel-api.html">The BCEL
   *     API</a>
   */
  JavaClass loadJavaClass(String className) throws ClassNotFoundException {
    return classRepository.loadClass(className);
  }

  /** Loads a system class available in JVM runtime. */
  Class<?> loadSystemClass(String className) throws ClassNotFoundException {
    return extensionClassLoader.loadClass(className);
  }

  boolean isSystemClass(String className) {
    try {
      if (className.startsWith("[")) {
        // Array class
        return true;
      }
      loadSystemClass(className);
      return true;
    } catch (ClassNotFoundException ex) {
      return false;
    }
  }

  /**
   * Returns class names defined in the jar file.
   *
   * @param jarPath absolute path to the jar file
   */
  ImmutableSet<String> classesDefinedInJar(Path jarPath) {
    return jarFileToClasses.get(jarPath);
  }

  /**
   * Returns symbol references (maps from classes to class symbols, method symbols and field
   * symbols) found in the class path.
   */
  SymbolReferenceMaps findSymbolReferences() throws IOException {
    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();

    for (Path jar : inputClassPath) {
      for (JavaClass javaClass : listClassesInJar(jar)) {
        if (!isCompatibleClassFileVersion(javaClass)) {
          continue;
        }
        ClassFile source = new ClassFile(jar, javaClass.getClassName());
        builder.addAll(findSymbolReferences(source, javaClass));
      }
    }

    return builder.build();
  }

  /**
   * Returns true if {@code javaClass} file format is compatible with this tool. Currently
   * Java 8 and earlier are supported.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.1">Java
   *     Virtual Machine Specification: The ClassFile Structure: minor_version, major_version</a>
   */
  private static boolean isCompatibleClassFileVersion(JavaClass javaClass) {
    int classFileMajorVersion = javaClass.getMajor();
    return 45 <= classFileMajorVersion && classFileMajorVersion <= 52;
  }

  private static SymbolReferenceMaps.Builder findSymbolReferences(
      ClassFile source, JavaClass javaClass) {
    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();

    ConstantPool constantPool = javaClass.getConstantPool();
    Constant[] constants = constantPool.getConstantPool();
    for (Constant constant : constants) {
      if (constant == null) {
        continue;
      }
      byte constantTag = constant.getTag();
      switch (constantTag) {
        case Const.CONSTANT_Class:
          ConstantClass constantClass = (ConstantClass) constant;
          ClassSymbol classSymbol = makeSymbol(constantClass, constantPool, javaClass);
          // skip array class because it is provided by runtime
          if (classSymbol.getClassName().startsWith("[")) {
            break;
          }
          builder.addClassReference(source, classSymbol);
          break;
        case Const.CONSTANT_Methodref:
        case Const.CONSTANT_InterfaceMethodref:
          // Both ConstantMethodref and ConstantInterfaceMethodref are subclass of ConstantCP
          ConstantCP constantMethodref = (ConstantCP) constant;
          builder.addMethodReference(source, makeSymbol(constantMethodref, constantPool));
          break;
        case Const.CONSTANT_Fieldref:
          ConstantFieldref constantFieldref = (ConstantFieldref) constant;
          builder.addFieldReference(source, makeSymbol(constantFieldref, constantPool));
          break;
        default:
          break;
      }
    }

    return builder;
  }

  private static ConstantNameAndType constantNameAndType(
      ConstantCP constantCP, ConstantPool constantPool) {
    int nameAndTypeIndex = constantCP.getNameAndTypeIndex();
    Constant constantAtNameAndTypeIndex = constantPool.getConstant(nameAndTypeIndex);
    if (!(constantAtNameAndTypeIndex instanceof ConstantNameAndType)) {
      // This constant_pool entry must be a CONSTANT_NameAndType_info
      // as specified https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.2
      throw new ClassFormatException(
          "Failed to lookup nameAndType constant indexed at "
              + nameAndTypeIndex
              + ". However, the content is not ConstantNameAndType. It is "
              + constantAtNameAndTypeIndex);
    }
    return (ConstantNameAndType) constantAtNameAndTypeIndex;
  }

  private static ClassSymbol makeSymbol(
      ConstantClass constantClass, ConstantPool constantPool, JavaClass sourceClass) {
    int nameIndex = constantClass.getNameIndex();
    Constant classNameConstant = constantPool.getConstant(nameIndex);
    if (!(classNameConstant instanceof ConstantUtf8)) {
      // This constant_pool entry must be a CONSTANT_Utf8_info
      // as specified https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.1
      throw new ClassFormatException(
          "Failed to lookup ConstantUtf8 constant indexed at "
              + nameIndex
              + ". However, the content is not ConstantUtf8. It is "
              + classNameConstant);
    }
    ConstantUtf8 classNameConstantUtf8 = (ConstantUtf8) classNameConstant;
    // classNameConstantUtf8 has internal form of class names that uses '.' to separate identifiers
    String targetClassNameInternalForm = classNameConstantUtf8.getBytes();
    // Adjust the internal form to comply with binary names defined in JLS 13.1
    String targetClassName = targetClassNameInternalForm.replace('/', '.');
    String superClassName = sourceClass.getSuperclassName();
    boolean isInheritance = superClassName.equals(targetClassName);

    if (isInheritance) {
      // A relationship between a superclass and subclass needs special validation for 'final'.
      return new SuperClassSymbol(targetClassName);
    }
    return new ClassSymbol(targetClassName);
  }

  private static MethodSymbol makeSymbol(
      ConstantCP constantMethodref, ConstantPool constantPool) {
    String className = constantMethodref.getClass(constantPool);
    ConstantNameAndType constantNameAndType = constantNameAndType(constantMethodref, constantPool);
    String methodName = constantNameAndType.getName(constantPool);
    String descriptor = constantNameAndType.getSignature(constantPool);
    // constantMethodref is either ConstantMethodref or ConstantInterfaceMethodref
    boolean isInterfaceMethod = constantMethodref instanceof ConstantInterfaceMethodref;
    return new MethodSymbol(className, methodName, descriptor, isInterfaceMethod);
  }

  private static FieldSymbol makeSymbol(
      ConstantFieldref constantFieldref, ConstantPool constantPool) {
    // Either a class type or an interface type
    String className = constantFieldref.getClass(constantPool);
    ConstantNameAndType constantNameAndType = constantNameAndType(constantFieldref, constantPool);
    String fieldName = constantNameAndType.getName(constantPool);
    String descriptor = constantNameAndType.getSignature(constantPool);
    return new FieldSymbol(className, fieldName, descriptor);
  }

  static ImmutableSet<String> listInnerClassNames(JavaClass javaClass) {
    ImmutableSet.Builder<String> innerClassNames = ImmutableSet.builder();
    String topLevelClassName = javaClass.getClassName();
    ConstantPool constantPool = javaClass.getConstantPool();
    for (Attribute attribute : javaClass.getAttributes()) {
      if (attribute.getTag() != Const.ATTR_INNER_CLASSES) {
        continue;
      }
      // This innerClasses variable does not include double-nested inner classes
      InnerClasses innerClasses = (InnerClasses) attribute;
      for (InnerClass innerClass : innerClasses.getInnerClasses()) {
        int classIndex = innerClass.getInnerClassIndex();
        String innerClassName = constantPool.getConstantString(classIndex, Const.CONSTANT_Class);
        int outerClassIndex = innerClass.getOuterClassIndex();
        if (outerClassIndex > 0) {
          String outerClassName =
              constantPool.getConstantString(outerClassIndex, Const.CONSTANT_Class);
          String normalOuterClassName = outerClassName.replace('/', '.');
          if (!normalOuterClassName.equals(topLevelClassName)) {
            continue;
          }
        }

        // Class names stored in constant pool have '/' as separator. We want '.' (as binary name)
        String normalInnerClassName = innerClassName.replace('/', '.');
        innerClassNames.add(normalInnerClassName);
      }
    }
    return innerClassNames.build();
  }

  /**
   * Returns the first jar file {@link Path} defining the class. Null if the location is unknown.
   */
  @Nullable
  Path findClassLocation(String className) {
    // Initially this method used classLoader.loadClass().getProtectionDomain().getCodeSource().
    // However, it required the superclass of a target class to be loadable too; otherwise
    // ClassNotFoundException was raised. It was inconvenient because we only wanted to know the
    // location of the target class, and sometimes the superclass is unavailable.
    return Iterables.getFirst(classToJarFiles.get(className), null);
  }

  /**
   * Returns mapping from jar files to the names of the classes they define.
   *
   * @param jars absolute paths to jar files
   */
  @VisibleForTesting
  static ImmutableSetMultimap<Path, String> mapJarToClasses(List<Path> jars) throws IOException {
    ImmutableSetMultimap.Builder<Path, String> pathToClasses = ImmutableSetMultimap.builder();
    for (Path jar : jars) {
      for (String className : listClassNamesInJar(jar)) {
        pathToClasses.put(jar, className);
      }
    }
    return pathToClasses.build();
  }

  static ImmutableSet<String> listClassNamesInJar(Path jar) throws IOException {
    URL jarUrl = jar.toUri().toURL();
    // Setting parent as null because we don't want other classes than this jar file
    URLClassLoader classLoaderFromJar = new URLClassLoader(new URL[] {jarUrl}, null);

    // Leveraging Google Guava reflection as BCEL doesn't list classes in a jar file
    com.google.common.reflect.ClassPath classPath =
        com.google.common.reflect.ClassPath.from(classLoaderFromJar);

    return classPath.getAllClasses().stream()
        .map(ClassInfo::getName)
        .collect(toImmutableSet());
  }

  /**
   * Returns a set of {@link JavaClass}es which have entries in the {@code jar} through {@link
   * #classRepository}.
   */
  private ImmutableSet<JavaClass> listClassesInJar(Path jar) throws IOException {
    ImmutableSet.Builder<JavaClass> javaClasses = ImmutableSet.builder();
    for (String className : listClassNamesInJar(jar)) {
      try {
        JavaClass javaClass = classRepository.loadClass(className);
        javaClasses.add(javaClass);
      } catch (ClassNotFoundException ex) {
        // We couldn't find the class in the jar file where we found it.
        throw new IOException("Corrupt jar file " + jar + "; could not load " + className, ex);
      } catch (ClassFormatException ex) {
        // We couldn't load the class from the jar file where we found it.
        throw new IOException("Possible corrupt jar file " + jar + "; could not load " + className
            + "; " + ex.getMessage(), ex);
      }
    }
    return javaClasses.build();
  }

  /** Returns true if two class names (binary name JLS 13.1) have the same package. */
  static boolean classesInSamePackage(String classNameA, String classNameB) {
    // Because package name cannot have '.' at the beginning, we can use lastDotIndex=0 (that will
    // return empty string via substring below) for unnamed package.
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-7.html#jls-7.4.1
    int lastDotIndexA = Math.max(classNameA.lastIndexOf('.'), 0);
    int lastDotIndexB = Math.max(classNameB.lastIndexOf('.'), 0);
    String packageNameA = classNameA.substring(0, lastDotIndexA);
    String packageNameB = classNameB.substring(0, lastDotIndexB);
    return packageNameA.equals(packageNameB);
  }

  /** Returns true if {@code childClass} is a subclass of {@code parentClass}. */
  static boolean isClassSubClassOf(JavaClass childClass, JavaClass parentClass) {
    for (JavaClass superClass : getClassHierarchy(childClass)) {
      if (superClass.equals(parentClass)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the name of enclosing class for the class name (binary name JLS 13.1). Null if the
   * class is not nested.
   */
  static String enclosingClassName(String className) {
    int lastDollarIndex = className.lastIndexOf('$');
    if (lastDollarIndex < 0) {
      return null;
    }
    return className.substring(0, lastDollarIndex);
  }

  /**
   * Returns true if {@code parentJavaClass} is not {@code final} and {@code childJavaClass} is not
   * overriding any {@code final} method of {@code parentJavaClass}.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10">Java
   *     Virtual Machine Specification: 4.10. Verification of class Files</a>
   */
  boolean hasValidSuperclass(JavaClass childJavaClass, JavaClass parentJavaClass) {
    if (parentJavaClass.isFinal()) {
      return false;
    }

    for (Method method : childJavaClass.getMethods()) {
      for (JavaClass parentClass : getClassHierarchy(parentJavaClass)) {
        for (final Method methodInParent : parentClass.getMethods()) {
          if (methodInParent.getName().equals(method.getName())
              && methodInParent.getSignature().equals(method.getSignature())
              && methodInParent.isFinal()) {
            return false;
          }
        }
      }
    }

    return true;
  }

  /**
   * Returns the indices of all class symbol references to {@code targetClassName} in the constant
   * pool of {@code sourceJavaClass}.
   */
  ImmutableSet<Integer> constantPoolIndexForClass(
      JavaClass sourceJavaClass, String targetClassName) {
    ImmutableSet.Builder<Integer> constantPoolIndicesForTarget = ImmutableSet.builder();

    ConstantPool sourceConstantPool = sourceJavaClass.getConstantPool();
    Constant[] constantPoolEntries = sourceConstantPool.getConstantPool();
    for (int poolIndex = 0; poolIndex < constantPoolEntries.length; poolIndex++) {
      Constant constant = constantPoolEntries[poolIndex];
      if (constant == null) {
        continue; // constantPool uses index starting from 1. 0th entry is null.
      }
      byte constantTag = constant.getTag();
      if (constantTag == Const.CONSTANT_Class) {
        ConstantClass constantClass = (ConstantClass) constant;
        ClassSymbol classSymbol = makeSymbol(constantClass, sourceConstantPool, sourceJavaClass);
        if (targetClassName.equals(classSymbol.getClassName())) {
          constantPoolIndicesForTarget.add(poolIndex);
        }
      }
    }

    return constantPoolIndicesForTarget.build();
  }

  /**
   * Returns true if {@link SymbolReference#getSourceClassName()} has a method that has an exception
   * handler for {@link NoClassDefFoundError}.
   */
  boolean catchesNoClassDefFoundError(SymbolReference reference) {
    String sourceClassName = reference.getSourceClassName();
    try {
      JavaClass sourceJavaClass = loadJavaClass(sourceClassName);
      ClassGen classGen = new ClassGen(sourceJavaClass);

      for (Method method : sourceJavaClass.getMethods()) {
        MethodGen methodGen = new MethodGen(method, sourceClassName, classGen.getConstantPool());
        CodeExceptionGen[] exceptionHandlers = methodGen.getExceptionHandlers();
        for (CodeExceptionGen codeExceptionGen : exceptionHandlers) {
          ObjectType catchType = codeExceptionGen.getCatchType();
          if (catchType == null) {
            continue;
          }
          String caughtClassName = catchType.getClassName();
          if (NoClassDefFoundError.class.getName().equals(caughtClassName)) {
            // NoClassDefFoundError is caught in the source class
            return true;
          }
        }
      }
    } catch (ClassNotFoundException ex) {
      // Because the reference in the argument was extracted from the source class file,
      // the source class should be found.
      throw new ClassFormatException(
          "The source class in the reference is no longer available in the class path", ex);
    }

    // The source class does not have a method that catches NoClassDefFoundError
    return false;
  }

  /**
   * Returns true if the class symbol reference is unused in the source class file. It checks
   * following places for the usage in the source class:
   *
   * <ul>
   *   <li>Superclass and interfaces
   *   <li>Type signatures of fields and methods
   *   <li>Constant pool entries that refer to a CONSTANT_Class_info structure
   *   <li>Java Virtual Machine instructions that takes a symbolic reference to a class
   *   <li>The exception table and exception handlers of methods
   * </ul>
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.2">Java
   *     Virtual Machine Specification: The CONSTANT_Fieldref_info, CONSTANT_Methodref_info, and
   *     CONSTANT_InterfaceMethodref_info Structures</a>
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5">Java
   *     Virtual Machine Specification: Instructions</a>
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-2.html#jvms-2.10">Java
   *     Virtual Machine Specification: Exceptions</a>
   */
  boolean isUnusedClassSymbolReference(ClassSymbolReference reference) {
    if (reference.isSubclass()) {
      // The target class is used in class inheritance
      return false;
    }

    String sourceClassName = reference.getSourceClassName();
    String targetClassName = reference.getTargetClassName();

    try {
      JavaClass sourceJavaClass = loadJavaClass(sourceClassName);

      for (String interfaceName: sourceJavaClass.getInterfaceNames()) {
        if (interfaceName.equals(targetClassName)) {
          // The target class is used in interfaces
          return false;
        }
      }

      ImmutableSet<Integer> targetConstantPoolIndices =
          constantPoolIndexForClass(sourceJavaClass, targetClassName);
      Verify.verify(
          !targetConstantPoolIndices.isEmpty(),
          "When checking a class reference from %s to %s, the reference to the target class is no"
              + " longer found in the source class's constant pool.", // This should not happen
          sourceJavaClass.getClassName(),
          targetClassName);

      ConstantPool sourceConstantPool = sourceJavaClass.getConstantPool();
      Constant[] constantPoolEntries = sourceConstantPool.getConstantPool();
      for (Constant constant : constantPoolEntries) {
        if (constant == null) {
          continue;
        }
        switch (constant.getTag()) {
          case Const.CONSTANT_Methodref:
          case Const.CONSTANT_InterfaceMethodref:
          case Const.CONSTANT_Fieldref:
            ConstantCP constantCp = (ConstantCP) constant;
            int classIndex = constantCp.getClassIndex();
            if (targetConstantPoolIndices.contains(classIndex)) {
              // The class reference is used in another constant pool
              return false;
            }
            break;
        }
      }

      for (Field field : sourceJavaClass.getFields()) {
        // Type.toString returns binary name (for example, io.grpc.MethodDescriptor)
        String fieldTypeSignature = field.getType().toString();
        if (targetClassName.equals(fieldTypeSignature)) {
          return false;
        }
      }

      ClassGen classGen = new ClassGen(sourceJavaClass);
      for (Method method : sourceJavaClass.getMethods()) {

        if (targetClassName.equals(method.getReturnType().toString())) {
          return false;
        }
        for (Type argumentType : method.getArgumentTypes()) {
          String argumentTypeSignature = argumentType.toString();
          if (targetClassName.equals(argumentTypeSignature)) {
            return false;
          }
        }

        MethodGen methodGen = new MethodGen(method, sourceClassName, classGen.getConstantPool());
        InstructionList instructionList = methodGen.getInstructionList();
        if (instructionList != null) {
          for (InstructionHandle instructionHandle : instructionList) {
            Instruction instruction = instructionHandle.getInstruction();
            if (instruction instanceof CPInstruction) {
              // Checking JVM instructions that take a symbolic reference to a class in
              // JVM Instruction Set
              // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-6.html#jvms-6.5
              int classIndex = ((CPInstruction) instruction).getIndex();
              if (targetConstantPoolIndices.contains(classIndex)) {
                // The target class is used in a JVM instruction (including `new`).
                return false;
              }
            }
          }
        }

        // Exception table
        ExceptionTable exceptionTable = method.getExceptionTable();
        if (exceptionTable != null) {
          int[] exceptionIndexTable = exceptionTable.getExceptionIndexTable();
          for (int exceptionIndexTableEntry : exceptionIndexTable) {
            if (targetConstantPoolIndices.contains(exceptionIndexTableEntry)) {
              // The target class is used in throws clause
              return false;
            }
          }
        }

        // Exception handlers
        CodeExceptionGen[] exceptionHandlers = methodGen.getExceptionHandlers();
        for (CodeExceptionGen codeExceptionGen : exceptionHandlers) {
          ObjectType catchType = codeExceptionGen.getCatchType();
          if (catchType == null) {
            continue;
          }
          String caughtClassName = catchType.getClassName();
          if (caughtClassName != null && caughtClassName.equals(targetClassName)) {
            // The target class is used in catch clause
            return false;
          }
        }
      }

    } catch (ClassNotFoundException ex) {
      // Because the reference in the argument was extracted from the source class file,
      // the source class should be found.
      throw new ClassFormatException(
          "The source class in the reference is no longer available in the class path", ex);
    }

    // The target class is unused
    return true;
  }

  /**
   * Returns the target class and its superclasses in order (with {@link Object} last). If any can't
   * be found, the list stops with the previous one.
   */
  static Iterable<JavaClass> getClassHierarchy(JavaClass targetClass) {
    return SUPERCLASSES.breadthFirst(targetClass);
  }

  private static final Traverser<JavaClass> SUPERCLASSES =
      Traverser.forTree(
          javaClass -> {
            try {
              JavaClass superClass = javaClass.getSuperClass();
              return superClass == null ? ImmutableSet.of() : ImmutableSet.of(superClass);
            } catch (ClassNotFoundException e) {
              return ImmutableSet.of();
            }
          });
}
