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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantMethodref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.InnerClass;
import org.apache.bcel.classfile.InnerClasses;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.SyntheticRepository;

/**
 * Class to read symbolic references in Java class files and to verify the availability
 * of references in them, through the input class path for a static linkage check.
 */
class ClassDumper {

  private final ImmutableList<Path> inputClasspath;
  private final SyntheticRepository syntheticRepository;
  private final ClassLoader classLoader;
  private final ImmutableSetMultimap<Path, String> jarFileToClasses;

  ImmutableList<Path> getInputClasspath() {
    return inputClasspath;
  }

  static ClassDumper create(List<Path> jarFilePaths) throws IOException, ClassNotFoundException {
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

  JavaClass loadJavaClass(String javaClassName) throws ClassNotFoundException {
    return syntheticRepository.loadClass(javaClassName);
  }

  /**
   *  Lists all method references from the Java class file. The output corresponds to
   *  CONSTANT_Methodref_info entries in the .class file's constant pool. The output list includes
   *  both (internal) methods defined in the file and (external) methods called by the class.
   *
   * @param javaClass .class file to list its method references
   * @return list of the method signatures with their fully-qualified classes
   */
  static ImmutableList<FullyQualifiedMethodSignature> listMethodReferences(JavaClass javaClass) {
    ImmutableList.Builder<FullyQualifiedMethodSignature> methodReferences = ImmutableList.builder();
    ConstantPool constantPool = javaClass.getConstantPool();
    Constant[] constants = constantPool.getConstantPool();
    List<Constant> methodrefConstants = Arrays.stream(constants)
            .filter(Predicates.notNull())
            .filter(constant -> constant.getTag() == Const.CONSTANT_Methodref)
            .collect(Collectors.toList());
    for (Constant constant : methodrefConstants) {
      ConstantMethodref constantMethodref = (ConstantMethodref) constant;
      String classNameInMethodReference = constantMethodref.getClass(constantPool);
      int nameAndTypeIndex = constantMethodref.getNameAndTypeIndex();
      Constant constantAtNameAndTypeIndex = constantPool.getConstant(nameAndTypeIndex);
      if (!(constantAtNameAndTypeIndex instanceof ConstantNameAndType)) {
        // This constant_pool entry must be a CONSTANT_NameAndType_info
        // as specified https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.2
        throw new ClassFormatException(
            "Failed to lookup nameAndType constant indexed "
                + nameAndTypeIndex
                + ". This class file is not compliant with CONSTANT_Methodref_info specification");
      }
      ConstantNameAndType constantNameAndType = (ConstantNameAndType) constantAtNameAndTypeIndex;
      String methodName = constantNameAndType.getName(constantPool);
      String descriptor = constantNameAndType.getSignature(constantPool);
      FullyQualifiedMethodSignature methodref =
          new FullyQualifiedMethodSignature(classNameInMethodReference, methodName, descriptor);
      methodReferences.add(methodref);
    }
    return methodReferences.build();
  }

  /**
   * Scans class files in the jar file and returns a {@link SymbolReferenceSet} populated with
   * symbolic references.
   *
   * @param jarFilePath absolute path to a jar file
   * @return symbol references and classes defined in the jar file
   */
  static SymbolReferenceSet scanSymbolReferencesInJar(Path jarFilePath)
      throws ClassNotFoundException, IOException {
    Preconditions.checkArgument(
        jarFilePath.isAbsolute(), "The input jar file path is not an absolute path");
    Preconditions.checkArgument(
        Files.isReadable(jarFilePath), "The input jar file path is not readable");

    SymbolReferenceSet.Builder symbolTableBuilder = SymbolReferenceSet.builder();
    for (JavaClass javaClass : topLevelJavaClassesInJar(jarFilePath)) {
      symbolTableBuilder.merge(scanSymbolReferencesInClass(javaClass));
    }
    return symbolTableBuilder.build();
  }

  private static SymbolReferenceSet scanSymbolReferencesInClass(JavaClass javaClass) {
    SymbolReferenceSet.Builder symbolTableBuilder = SymbolReferenceSet.builder();
    ImmutableSet.Builder<MethodSymbolReference> methodReferences =
        symbolTableBuilder.methodReferencesBuilder();
    ImmutableSet.Builder<FieldSymbolReference> fieldReferences =
        symbolTableBuilder.fieldReferencesBuilder();

    // TODO(suztomo): Read class references (inheritance) from javaClass file
    ImmutableSet.Builder<ClassSymbolReference> classReferences =
        symbolTableBuilder.classReferencesBuilder();

    String sourceClassName = javaClass.getClassName();
    ConstantPool constantPool = javaClass.getConstantPool();
    Constant[] constants = constantPool.getConstantPool();
    for (Constant constant : constants) {
      if (constant == null) {
        continue;
      }
      byte constantTag = constant.getTag();
      switch (constantTag) {
        case Const.CONSTANT_Methodref:
          ConstantMethodref constantMethodref = (ConstantMethodref) constant;
          methodReferences.add(constantToMethodReference(constantMethodref, constantPool,
              sourceClassName));
          break;
        case Const.CONSTANT_Fieldref:
          ConstantFieldref constantFieldref = (ConstantFieldref) constant;
          fieldReferences.add(constantToFieldReference(constantFieldref, constantPool,
              sourceClassName));
          break;
        case Const.CONSTANT_Class:
          // TODO(suztomo): handle class reference
          break;
        default:
          break;
      }
    }

    return symbolTableBuilder.build();
  }

  static ConstantNameAndType constantNameAndTypeFromConstantCP(
      ConstantCP constantCP, ConstantPool constantPool) {
    int nameAndTypeIndex = constantCP.getNameAndTypeIndex();
    Constant constantAtNameAndTypeIndex = constantPool.getConstant(nameAndTypeIndex);
    if (!(constantAtNameAndTypeIndex instanceof ConstantNameAndType)) {
      // This constant_pool entry must be a CONSTANT_NameAndType_info
      // as specified https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.2
      throw new ClassFormatException(
          "Failed to lookup nameAndType constant indexed "
              + nameAndTypeIndex
              + ". This class file is not compliant with CONSTANT_Methodref_info specification");
    }
    return (ConstantNameAndType) constantAtNameAndTypeIndex;
  }

  private static MethodSymbolReference constantToMethodReference(
      ConstantMethodref constantMethodref, ConstantPool constantPool, String sourceClassName) {
    String classNameInMethodReference = constantMethodref.getClass(constantPool);
    ConstantNameAndType constantNameAndType =
        constantNameAndTypeFromConstantCP(constantMethodref, constantPool);
    String methodName = constantNameAndType.getName(constantPool);
    String descriptor = constantNameAndType.getSignature(constantPool);
    MethodSymbolReference methodReference = MethodSymbolReference.builder()
        .setSourceClassName(sourceClassName)
        .setMethodName(methodName)
        .setTargetClassName(classNameInMethodReference)
        .setDescriptor(descriptor)
        .build();
    return methodReference;
  }

  private static FieldSymbolReference constantToFieldReference(ConstantFieldref constantFieldref,
      ConstantPool constantPool, String sourceClassName) {
    // Either a class type or an interface type
    String classNameInFieldReference = constantFieldref.getClass(constantPool);
    ConstantNameAndType constantNameAndType =
        constantNameAndTypeFromConstantCP(constantFieldref, constantPool);
    String fieldName = constantNameAndType.getName(constantPool);

    FieldSymbolReference fieldSymbolReference = FieldSymbolReference.builder()
        .setSourceClassName(sourceClassName)
        .setFieldName(fieldName)
        .setTargetClassName(classNameInFieldReference)
        .build();
    return fieldSymbolReference;
  }

  /**
   * Lists external method references for a class. The returned list does not include method
   * references that point to other classes defined in the same jar file as the class.
   *
   * @param className class name to list its method references. The class must be available through
   *     the class loader and BCEL repository.
   * @return list of external method references from the class
   */
  ImmutableSet<FullyQualifiedMethodSignature> listExternalMethodReferences(String className) {
    try {
      Class clazz = classLoader.loadClass(className);
      CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
      if (codeSource == null) {
        // Code in bootstrap class loader (e.g., javax) does not have source
        return ImmutableSet.of();
      }
      Path jarPathForTheClass = Paths.get(codeSource.getLocation().toURI());
      Set<String> classesDefinedInSameJar = jarFileToClasses.get(jarPathForTheClass);

      // Follows usage graph from the internal classes to external references
      return findExternalMethodReferencesByUsageGraph(
          ImmutableSet.of(className), classesDefinedInSameJar);
    } catch (ClassNotFoundException | URISyntaxException ex) {
      // TODO: Investigate why 'mvn exec:java' causes ClassNotFoundException for Guava
      // Running withinStaticLinkageChecker via IntelliJ does not cause the problem
      throw new RuntimeException(
          "There was an error in reading method references from the class: " + className, ex);
    }
  }

  /**
   * Finds external method references by following usage graph from {@code initialClassNames}
   * through other internal classes. For example, given following usage graph of 4 classes in 2 jar
   * files:
   *
   * <pre>
   *   'Class A' → 'Class B' → 'Class C' → 'Class D'
   *   |←         in X.jar              →|← in Y.jar →
   * </pre>
   *
   * and {@code initialClassNames: ['Class A']}, this function returns list of method references to
   * 'Class D', not including references to 'Class B' or 'Class C'.
   *
   * @param initialClassNames list of classes to follow usage graph. They must be within same jar
   *     file.
   * @param classesDefinedInSameJar set of classes defined in the same jar file as {@code
   *     initialClassNames}
   * @return set of method references external to the jar file of {@code initialClassNames}
   * @throws ClassNotFoundException when there is a problem in accessing a class via BCEL repository
   */
  private ImmutableSet<FullyQualifiedMethodSignature>
      findExternalMethodReferencesByUsageGraph(
          Set<String> initialClassNames,
          Set<String> classesDefinedInSameJar)
          throws ClassNotFoundException {
    Set<String> visitedClasses = new HashSet<>(initialClassNames);
    Queue<String> classQueue = new ArrayDeque<>(initialClassNames);
    ImmutableSet.Builder<FullyQualifiedMethodSignature> nextExternalMethodReferences =
        ImmutableSet.builder();
    while (!classQueue.isEmpty()) {
      String internalClassName = classQueue.remove();
      JavaClass internalJavaClass = syntheticRepository.loadClass(internalClassName);
      List<FullyQualifiedMethodSignature> nextMethodReferencesFromInternalClass =
          listMethodReferences(internalJavaClass);
      for (FullyQualifiedMethodSignature methodReference : nextMethodReferencesFromInternalClass) {
        String nextClassName = methodReference.getClassName();
        if (classesDefinedInSameJar.contains(nextClassName)) {
          if (visitedClasses.add(nextClassName)) {
            classQueue.add(nextClassName);
          }
        } else {
          // While iterating the graph, record method references external to the jar file
          nextExternalMethodReferences.add(methodReference);
        }
      }
    }
    return nextExternalMethodReferences.build();
  }

  static ImmutableList<FullyQualifiedMethodSignature> listMethodsOnClass(JavaClass javaClass) {
    List<MethodSignature> methods = listDeclaredMethods(javaClass);
    ImmutableList<FullyQualifiedMethodSignature> fullyQualifiedMethodSignatures = methods.stream()
        .map(method -> new FullyQualifiedMethodSignature(
            javaClass.getClassName(), method.getMethodName(), method.getDescriptor()))
        .collect(toImmutableList());
    return fullyQualifiedMethodSignatures;
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
          String outerClassName = constantPool.getConstantString(outerClassIndex,
              Const.CONSTANT_Class);
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
   * Lists method signatures from the class file. The output corresponds to entries in the
   * method table in the .class file.
   *
   * @param javaClass .class file to search methods
   * @return method and signature entries defined in the class file
   */
  static ImmutableList<MethodSignature> listDeclaredMethods(JavaClass javaClass) {
    Method[] methods = javaClass.getMethods();
    ImmutableList<MethodSignature> signatures = Arrays.stream(methods)
        .map(method -> new MethodSignature(method.getName(), method.getSignature()))
        .collect(toImmutableList());
    return signatures;
  }

  @VisibleForTesting
  Class[] methodDescriptorToClass(String methodDescriptor) {
    Type[] argumentTypes = Type.getArgumentTypes(methodDescriptor);
    Class[] parameterTypes =
        Arrays.stream(argumentTypes)
            .map(type -> bcelTypeToJavaClass(type, classLoader))
            .toArray(Class[]::new);
    return parameterTypes;
  }

  private static Class bcelTypeToJavaClass(Type type, ClassLoader classLoader) {
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

  @SuppressWarnings("unchecked")
  boolean methodDefinitionExists(FullyQualifiedMethodSignature methodReference)
      throws ClassNotFoundException {
    String className = methodReference.getClassName();
    MethodSignature methodSignature = methodReference.getMethodSignature();
    String methodName = methodSignature.getMethodName();
    Class[] parameterTypes = methodDescriptorToClass(methodSignature.getDescriptor());
    try {
      // Attempt 1: Find the class and method in the class loader
      // Class loader helps to resolve class hierarchy, such as methods defined in parent class
      Class clazz =
          className.startsWith("[")
              ? Array.class
              : classLoader.loadClass(className);
      if ("<init>".equals(methodName)) {
        clazz.getConstructor(parameterTypes);
      } else if ("clone".equals(methodName) && clazz == Array.class) {
        // Array's clone method is not returned by getMethod
        // https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html#getMethod-java.lang.String-java.lang.Class...-
        return true;
      } else {
        clazz.getMethod(methodSignature.getMethodName(),
            parameterTypes);
      }
      return true;
    } catch (NoSuchMethodException | ClassNotFoundException ex) {
      // Attempt 2: Find the class and method in BCEL API
      // BCEL helps to search availability of (package) private class, constructors and methods
      // that are inaccessible to Java's reflection API or the class loader.
      JavaClass javaClass = loadJavaClass(className);
      while (javaClass != null) {
        // Inherited methods need checking with the parent class name
        FullyQualifiedMethodSignature methodReferenceForClass = new FullyQualifiedMethodSignature(
            javaClass.getClassName(),
            methodName,
            methodSignature.getDescriptor()
        );
        List<FullyQualifiedMethodSignature> availableMethodsOnClass =
            ClassDumper.listMethodsOnClass(javaClass);
        if (availableMethodsOnClass.contains(methodReferenceForClass)) {
          return true;
        }
        // null if java.lang.Object
        javaClass = javaClass.getSuperClass();
      }
      return false;
    }
  }

  /**
   * @param jarFilePaths absolute paths to jar files
   * @return map of jar file paths to classes defined in them
   */
  private static ImmutableSetMultimap<Path, String> jarFilesToDefinedClasses(
      List<Path> jarFilePaths) throws IOException, ClassNotFoundException {
    ImmutableSetMultimap.Builder<Path, String> pathToClasses =
        ImmutableSetMultimap.builder();

    for (Path jarFilePath : jarFilePaths) {
      for (JavaClass javaClass: topLevelJavaClassesInJar(jarFilePath)) {
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

  static ImmutableSet<JavaClass> topLevelJavaClassesInJar(Path jarFilePath)
      throws IOException, ClassNotFoundException {
    String pathToJar = jarFilePath.toString();
    SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(pathToJar));
    ImmutableSet.Builder<JavaClass> javaClasses = ImmutableSet.builder();
    URL jarFileUrl = jarFilePath.toUri().toURL();
    for (ClassInfo classInfo : listTopLevelClassesFromJar(jarFileUrl)) {
      String className = classInfo.getName();
      JavaClass javaClass = repository.loadClass(className);
      javaClasses.add(javaClass);
    }
    return javaClasses.build();
  }
}
