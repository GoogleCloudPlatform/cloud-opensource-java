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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicates;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantMethodref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.util.SyntheticRepository;

/**
 * This class reads a Java class file to analyze following attributes:
 *
 * <ol>
 *   <li>source (defined methods) via Method fields in the class, and</li>
 *   <li>targets (what's attempted to be invoked) via the constant pool table of the class.</li>
 * </ol>
 */
class ClassDumper {

  /**
   *  Lists all method references from the Java class file. The output corresponds to
   *  CONSTANT_Methodref_info entries in the .class file's constant pool. The output list includes
   *  both (internal) methods defined in the file and (external) methods called by the class.
   *
   * @param javaClass .class file to list its method references
   * @return list of the method signatures with their fully-qualified classes
   */
  static List<FullyQualifiedMethodSignature> listMethodReferences(JavaClass javaClass) {
    List<FullyQualifiedMethodSignature> methodReferences = new ArrayList<>();
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
        throw new RuntimeException(
            "Failed to lookup nameAndType constant indexed " + nameAndTypeIndex
                + ". This class file is not compliant with CONSTANT_Methodref_info specification");
      }
      ConstantNameAndType constantNameAndType = (ConstantNameAndType) constantAtNameAndTypeIndex;
      String methodName = constantNameAndType.getName(constantPool);
      String descriptor = constantNameAndType.getSignature(constantPool);
      FullyQualifiedMethodSignature methodref =
          new FullyQualifiedMethodSignature(classNameInMethodReference, methodName, descriptor);
      methodReferences.add(methodref);
    }
    return methodReferences;
  }

  /**
   * Lists external method references for a class. The returned list does not include method
   * references that point to other classes defined in the same jar file as the class.
   *
   * @param className class name to list its method references. The class must be available through
   *     the class loader and BCEL repository.
   * @param jarFileToClasses mapping of jar file paths to classes. This helps to distinguish whether
   *     the class in a method reference is in the same jar file or not.
   * @param classLoader class loader to locate the jar file for the class
   * @param repository BCEL repository to list method references for the class
   * @return list of external method references from the class
   */
  static List<FullyQualifiedMethodSignature> listExternalMethodReferences(
      String className,
      SetMultimap<Path, String> jarFileToClasses,
      ClassLoader classLoader,
      SyntheticRepository repository) {
    try {
      JavaClass javaClass = repository.loadClass(className);
      Class clazz = classLoader.loadClass(className);
      CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
      if (codeSource == null) {
        // Code in bootstrap class loader (e.g., javax) does not have source
        return Collections.emptyList();
      }
      Path jarPathForTheClass = Paths.get(codeSource.getLocation().toURI());
      Set<String> classesDefinedInSameJar = jarFileToClasses.get(jarPathForTheClass);
      List<FullyQualifiedMethodSignature> nextMethodReferences = listMethodReferences(javaClass);
      List<FullyQualifiedMethodSignature> nextExternalMethodReferences = new ArrayList<>();
      List<String> referencedInternalClasses = new ArrayList<>();
      for (FullyQualifiedMethodSignature methodReference : nextMethodReferences) {
        String classNameInMethodReference = methodReference.getClassName();
        if (classesDefinedInSameJar.contains(classNameInMethodReference)) {
          // The methodReference is within the same jar but we want to follow usage graph
          String nextInternalClassName = methodReference.getClassName();
          referencedInternalClasses.add(nextInternalClassName);
        } else {
          nextExternalMethodReferences.add(methodReference);
        }
      }

      // Follows usage graph from the internal classes to external references
      List<FullyQualifiedMethodSignature> externalReferencesFromInternalClasses =
          findExternalMethodReferencesByUsageGraph(
              referencedInternalClasses, repository, classesDefinedInSameJar);
      nextExternalMethodReferences.addAll(externalReferencesFromInternalClasses);

      return nextExternalMethodReferences;
    } catch (ClassNotFoundException | URISyntaxException ex) {
      // TODO: Investigate why 'mvn exec:java' causes ClassNotFoundException for Guava
      // Running withinStaticLinkageChecker via IntelliJ does not cause the problem
      throw new RuntimeException(
          "There was an error in reading method references from the class: " + className, ex);
    }
  }

  /**
   * Finds external method references by following usage graph from {@code initialClassNames}
   * through other internal classes. For example, given following usage graph of 4 classes in
   * 2 jar files:
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
   * @param repository BCEL repository to list method references
   * @param classesDefinedInSameJar set of classes defined in the same jar file as
   *     {@code initialClassNames}
   * @return list of method references external to the jar file of {@code initialClassNames}
   * @throws ClassNotFoundException when there is a problem in accessing a class via BCEL repository
   */
  private static List<FullyQualifiedMethodSignature> findExternalMethodReferencesByUsageGraph(
      List<String> initialClassNames,
      SyntheticRepository repository,
      Set<String> classesDefinedInSameJar)
      throws ClassNotFoundException {
    Set<String> visitedClasses = new HashSet<>(initialClassNames);
    Queue<String> classQueue = new ArrayDeque<>(initialClassNames);
    List<FullyQualifiedMethodSignature> nextExternalMethodReferences = new ArrayList<>();
    while (!classQueue.isEmpty()) {
      String internalClassName = classQueue.remove();
      JavaClass internalJavaClass = repository.loadClass(internalClassName);
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
    return nextExternalMethodReferences;
  }

  /**
   *  Lists all method references from the class file. The output corresponds to
   *  CONSTANT_Methodref_info entries in the .class file's constant pool.
   *
   * @param classFileStream stream of a class file
   * @param fileName name of the file that contains class
   * @return list of the method signatures with their fully-qualified classes
   * @throws IOException when there is a problem in reading classFileStream
   */
  @VisibleForTesting
  static List<FullyQualifiedMethodSignature> listMethodReferences(InputStream classFileStream,
      String fileName) throws IOException {
    ClassParser parser = new ClassParser(classFileStream, fileName);
    JavaClass javaClass = parser.parse();
    return listMethodReferences(javaClass);
  }

  /**
   *  Lists all internal method references from the class file. The output corresponds to
   *  CONSTANT_Methodref_info entries in the .class file's constant pool.
   *
   * @param classFileStream stream of a class file
   * @param fileName name of the file that contains class
   * @return list of the method signatures with their fully-qualified classes
   * @throws IOException when there is a problem in reading classFileStream
   */
  public static List<FullyQualifiedMethodSignature> listInternalMethodReferences(
      InputStream classFileStream, String fileName) throws IOException {
    ClassParser parser = new ClassParser(classFileStream, fileName);
    JavaClass javaClass = parser.parse();
    List<FullyQualifiedMethodSignature> methodReferences = listMethodReferences(javaClass);
    Set<String> internalClasses = Sets.newHashSet(javaClass.getClassName());
    List<FullyQualifiedMethodSignature> internalMethodrefs =  methodReferences
            .stream()
            .filter(methodref -> internalClasses.contains(methodref.getClassName()))
            .collect(Collectors.toList());
    return internalMethodrefs;
  }

  /**
   *  Lists all external method references from the class file. The output corresponds to
   *  CONSTANT_Methodref_info entries in the .class file's constant pool.
   *
   * @param classFileStream stream of a class file
   * @param fileName name of the file that contains class
   * @return list of the method signatures with their fully-qualified classes
   * @throws IOException when there is a problem in reading classFileStream
   */
  public static List<FullyQualifiedMethodSignature> listExternalMethodReferences(
      InputStream classFileStream, String fileName) throws IOException {
    ClassParser parser = new ClassParser(classFileStream, fileName);
    JavaClass javaClass = parser.parse();
    List<FullyQualifiedMethodSignature> methodReferences = listMethodReferences(javaClass);
    Set<String> internalClasses = Sets.newHashSet(javaClass.getClassName());
    List<FullyQualifiedMethodSignature> externalMethodrefs = methodReferences
            .stream()
            .filter(methodref -> !internalClasses.contains(methodref.getClassName()))
            .collect(Collectors.toList());
    return externalMethodrefs;
  }

  /**
   * Lists method signatures from the class file. The output corresponds to entries in the
   * method table in the .class file.
   *
   * @param classFileStream stream of a class file
   * @param fileName name of the file that contains class
   * @return method and signature entries defined in the class file
   * @throws IOException when there is a problem in reading classFileStream
   */
  static List<MethodSignature> listDeclaredMethods(
      InputStream classFileStream, String fileName) throws IOException {
    ClassParser parser = new ClassParser(classFileStream, fileName);
    JavaClass javaClass = parser.parse();
    return listDeclaredMethods(javaClass);
  }

  /**
   * Lists method signatures from the class file. The output corresponds to entries in the
   * method table in the .class file.
   *
   * @param javaClass .class file to search methods
   * @return method and signature entries defined in the class file
   */
  static List<MethodSignature> listDeclaredMethods(JavaClass javaClass) {
    Method[] methods = javaClass.getMethods();
    List<MethodSignature> signatures = Arrays.stream(methods)
        .map(method -> new MethodSignature(method.getName(), method.getSignature()))
        .collect(Collectors.toList());
    return signatures;
  }

  /**
   * Lists the content of the constant pool table in the .class file.
   *
   * @param inputStream stream of a class file
   * @param fileName name of the file that contains class
   * @return String representation of constant pool entries
   * @throws IOException when there is a problem in reading classFileStream
   */
  @VisibleForTesting
  static List<String> listConstantPool(InputStream inputStream, String fileName)
      throws IOException {
    ClassParser parser = new ClassParser(inputStream, fileName);
    JavaClass javaClass = parser.parse();
    ConstantPool constantPool = javaClass.getConstantPool();
    Constant[] constants = constantPool.getConstantPool();

    // Items in ConstantPool. E.g.,
    //  [CONSTANT_Methodref[10](class_index = 297, name_and_type_index = 298),
    //   CONSTANT_Class[7](name_index = 299),
    //   CONSTANT_Methodref[10](class_index = 297, name_and_type_index = 300), ...
    List<String> constantStrings = Arrays.stream(constants)
            .filter(Predicates.notNull())
            .map(Constant::toString)
            .collect(Collectors.toList());
    return constantStrings;
  }
}
