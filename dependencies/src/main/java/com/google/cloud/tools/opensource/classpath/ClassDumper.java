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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantMethodref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.InnerClass;
import org.apache.bcel.classfile.InnerClasses;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.SyntheticRepository;

/**
 * Class to read symbol references in Java class files and to verify the availability of references
 * in them, through the input class path for a static linkage check.
 */
class ClassDumper {

  private final ImmutableList<Path> inputClasspath;
  private final SyntheticRepository syntheticRepository;
  private final ClassLoader classLoader;
  private final ImmutableSetMultimap<Path, String> jarFileToClasses;

  ImmutableList<Path> getInputClasspath() {
    return inputClasspath;
  }

  static ClassDumper create(List<Path> jarFilePaths) throws IOException {
    // Creates classpath in the same order as inputClasspath for BCEL API
    String pathAsString =
        jarFilePaths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    ClassPath classPath = new ClassPath(pathAsString);
    SyntheticRepository syntheticRepository = SyntheticRepository.getInstance(classPath);

    URL[] jarFileUrls = jarFilePaths.stream().map(jarPath -> {
      try {
        return jarPath.toUri().toURL();
      } catch (MalformedURLException ex) {
        throw new IllegalArgumentException("Jar file " + jarPath + " was not converted to URL",
            ex);
      }
    }).toArray(URL[]::new);
    URLClassLoader classLoaderFromJars =
        new URLClassLoader(jarFileUrls, ClassLoader.getSystemClassLoader());

    return new ClassDumper(
        jarFilePaths,
        syntheticRepository,
        classLoaderFromJars,
        jarFilesToDefinedClasses(jarFilePaths));
  }

  private ClassDumper(
      List<Path> inputClasspath,
      SyntheticRepository syntheticRepository,
      ClassLoader classLoader,
      SetMultimap<Path, String> jarFileToClasses) {
    this.inputClasspath = ImmutableList.copyOf(inputClasspath);
    this.syntheticRepository = syntheticRepository;
    this.classLoader = classLoader;
    this.jarFileToClasses = ImmutableSetMultimap.copyOf(jarFileToClasses);
  }

  /**
   * Returns {@link JavaClass} for {@code className} in the input class path using the BCEL API.
   *
   * @see <a href="https://commons.apache.org/proper/commons-bcel/manual/bcel-api.html">The BCEL
   *     API</a>
   */
  JavaClass loadJavaClass(String className) throws ClassNotFoundException {
    return syntheticRepository.loadClass(className);
  }

  /**
   * Returns {@link Class} for {@code className} in the input class path using a Java class loader.
   */
  Class<?> loadClass(String className) throws ClassNotFoundException {
    return classLoader.loadClass(className);
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
   * Scans class files in the jar file and returns a {@link SymbolReferenceSet} populated with
   * symbol references.
   *
   * @param jarFilePath absolute path to a jar file
   */
  static SymbolReferenceSet scanSymbolReferencesInJar(Path jarFilePath)
      throws IOException {
    checkArgument(jarFilePath.isAbsolute(), "The input jar file path is not an absolute path");
    checkArgument(Files.isReadable(jarFilePath), "The input jar file path is not readable");

    SymbolReferenceSet.Builder symbolTableBuilder = SymbolReferenceSet.builder();
    for (JavaClass javaClass : topLevelJavaClassesInJar(jarFilePath)) {
      symbolTableBuilder.addAll(scanSymbolReferencesInClass(javaClass));
    }
    return symbolTableBuilder.build();
  }

  private static SymbolReferenceSet scanSymbolReferencesInClass(JavaClass javaClass) {
    SymbolReferenceSet.Builder symbolTableBuilder = SymbolReferenceSet.builder();
    ImmutableSet.Builder<ClassSymbolReference> classReferences =
        symbolTableBuilder.classReferencesBuilder();
    ImmutableSet.Builder<MethodSymbolReference> methodReferences =
        symbolTableBuilder.methodReferencesBuilder();
    ImmutableSet.Builder<FieldSymbolReference> fieldReferences =
        symbolTableBuilder.fieldReferencesBuilder();

    String sourceClassName = javaClass.getClassName();
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
          ClassSymbolReference classSymbolReference =
              constantToClassReference(constantClass, constantPool, sourceClassName);
          // skip array class because it is provided by runtime
          if (!classSymbolReference.getTargetClassName().startsWith("[")) {
            classReferences.add(classSymbolReference);
          }
          break;
        case Const.CONSTANT_Methodref:
          ConstantMethodref constantMethodref = (ConstantMethodref) constant;
          methodReferences.add(
              constantToMethodReference(constantMethodref, constantPool, sourceClassName));
          break;
        case Const.CONSTANT_Fieldref:
          ConstantFieldref constantFieldref = (ConstantFieldref) constant;
          fieldReferences.add(
              constantToFieldReference(constantFieldref, constantPool, sourceClassName));
          break;
        default:
          break;
      }
    }

    return symbolTableBuilder.build();
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

  private static ClassSymbolReference constantToClassReference(
      ConstantClass constantClass, ConstantPool constantPool, String sourceClassName) {
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
    ConstantUtf8 classNameConstantUtf8 = (ConstantUtf8)classNameConstant;
    // classNameConstantUtf8 has internal form of class names that uses '.' to separate identifiers
    String targetClassNameInternalForm = classNameConstantUtf8.getBytes();
    // Adjust the internal form to comply with binary names defined in JLS 13.1
    String targetClassName = targetClassNameInternalForm.replace('/', '.');
    ClassSymbolReference classReference = ClassSymbolReference.builder()
        .setSourceClassName(sourceClassName)
        .setTargetClassName(targetClassName).build();
    return classReference;
  }

  private static MethodSymbolReference constantToMethodReference(
      ConstantMethodref constantMethodref, ConstantPool constantPool, String sourceClassName) {
    String classNameInMethodReference = constantMethodref.getClass(constantPool);
    ConstantNameAndType constantNameAndType = constantNameAndType(constantMethodref, constantPool);
    String methodName = constantNameAndType.getName(constantPool);
    String descriptor = constantNameAndType.getSignature(constantPool);
    MethodSymbolReference methodReference =
        MethodSymbolReference.builder()
            .setSourceClassName(sourceClassName)
            .setMethodName(methodName)
            .setTargetClassName(classNameInMethodReference)
            .setDescriptor(descriptor)
            .build();
    return methodReference;
  }

  private static FieldSymbolReference constantToFieldReference(
      ConstantFieldref constantFieldref, ConstantPool constantPool, String sourceClassName) {
    // Either a class type or an interface type
    String classNameInFieldReference = constantFieldref.getClass(constantPool);
    ConstantNameAndType constantNameAndType = constantNameAndType(constantFieldref, constantPool);
    String fieldName = constantNameAndType.getName(constantPool);

    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setSourceClassName(sourceClassName)
            .setFieldName(fieldName)
            .setTargetClassName(classNameInFieldReference)
            .build();
    return fieldSymbolReference;
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

  @VisibleForTesting
  Class<?>[] methodDescriptorToClass(String methodDescriptor) {
    Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
    Class<?>[] parameterTypes =
        Arrays.stream(argumentTypes)
            .map(type -> bcelTypeToJavaClass(type, classLoader))
            .toArray(Class[]::new);
    return parameterTypes;
  }

  /**
   * Returns the jar file URL of a class in the class path. Null if the information is unavailable.
   */
  URL findClassLocation(String className) throws ClassNotFoundException {
    Class<?> clazz = loadClass(className);
    CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
    if (codeSource == null) {
      // javax.activation.SecuritySupport is known to return null here
      return null;
    }
    return codeSource.getLocation();
  }

  private static Class<?> bcelTypeToJavaClass(Type type, ClassLoader classLoader) {
    switch (type.getType()) {
      case Const.T_BOOLEAN:
        return boolean.class;
      case Const.T_INT:
        return int.class;
      case Const.T_SHORT:
        return short.class;
      case Const.T_BYTE:
        return byte.class;
      case Const.T_LONG:
        return long.class;
      case Const.T_DOUBLE:
        return double.class;
      case Const.T_FLOAT:
        return float.class;
      case Const.T_CHAR:
        return char.class;
      case Const.T_ARRAY:
        return Object[].class;
      default:
        String typeName = type.toString();
        try {
          return classLoader.loadClass(typeName);
        } catch (ClassNotFoundException ex) {
          return null;
        }
    }
  }

  /**
   * @param jarFilePaths absolute paths to jar files
   * @return map of jar file paths to classes defined in them
   */
  private static ImmutableSetMultimap<Path, String> jarFilesToDefinedClasses(
      List<Path> jarFilePaths) throws IOException {
    ImmutableSetMultimap.Builder<Path, String> pathToClasses = ImmutableSetMultimap.builder();

    for (Path jarFilePath : jarFilePaths) {
      for (JavaClass javaClass : topLevelJavaClassesInJar(jarFilePath)) {
        pathToClasses.put(jarFilePath, javaClass.getClassName());
        // This does not take double-nested classes. As long as such classes are accessed
        // only from the outer class, static linkage checker does not report false positives
        // TODO(suztomo): enhance this so that it can work with double-nested classes
        pathToClasses.putAll(jarFilePath, listInnerClassNames(javaClass));
      }
    }
    return pathToClasses.build();
  }

  private static ImmutableSet<ClassInfo> listTopLevelClassesFromJar(URL jarFileUrl)
      throws IOException {
    URL[] jarFileUrls = new URL[] {jarFileUrl};

    // Setting parent as null because we don't want other classes than this jar file
    URLClassLoader classLoaderFromJar = new URLClassLoader(jarFileUrls, null);

    // Leveraging Google Guava reflection as BCEL doesn't list classes in a jar file
    com.google.common.reflect.ClassPath classPath =
        com.google.common.reflect.ClassPath.from(classLoaderFromJar);

    // Nested (inner) classes reside in one of top-level class files.
    ImmutableSet<ClassInfo> allClassesInJar = classPath.getTopLevelClasses();
    return allClassesInJar;
  }

  private static ImmutableSet<JavaClass> topLevelJavaClassesInJar(Path jarFilePath)
      throws IOException {
    String pathToJar = jarFilePath.toString();
    SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(pathToJar));
    ImmutableSet.Builder<JavaClass> javaClasses = ImmutableSet.builder();
    URL jarFileUrl = jarFilePath.toUri().toURL();
    for (ClassInfo classInfo : listTopLevelClassesFromJar(jarFileUrl)) {
      String className = classInfo.getName();
      try {
        JavaClass javaClass = repository.loadClass(className);
        javaClasses.add(javaClass);
      } catch (ClassNotFoundException ex) {
        // We couldn't load the class from the jar file where we found it.
        throw new IOException("Corrupt jar file " + jarFilePath + "; could not load " + className);
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

  /**
   * Returns the name of enclosing class for the class name (binary name JLS 13.1). If the class is
   * not nested, then it returns the class name.
   */
  static String enclosingClassName(String className) {
    int lastDollarIndex = className.lastIndexOf('$');
    if (lastDollarIndex < 0) {
      return className;
    }
    return className.substring(0, lastDollarIndex);
  }
}
