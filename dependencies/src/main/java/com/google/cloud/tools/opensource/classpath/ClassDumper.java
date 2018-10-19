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
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
      String className = constantMethodref.getClass(constantPool);
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
      FullyQualifiedMethodSignature methodref = new FullyQualifiedMethodSignature(className,
          methodName, descriptor);
      methodReferences.add(methodref);
    }
    return methodReferences;
  }

  /**
   * Lists external method references for a class. The returned list does not include method
   * references that points to a class defined in the same jar file as the class specified in the
   * first argument.
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
      Map<Path, Set<String>> jarFileToClasses,
      ClassLoader classLoader,
      SyntheticRepository repository) {
    try {
      JavaClass javaClass = repository.loadClass(className);
      Class clazz = classLoader.loadClass(className);
      CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
      if (codeSource == null) {
        // Code in bootstrap class loader (e.g., javax) will not have source and we do not
        // need it
        return Collections.emptyList();
      }
      Path jarPathForTheClass = Paths.get(codeSource.getLocation().toURI());
      Set<String> classesDefinedInSameJar = jarFileToClasses.get(jarPathForTheClass);
      if (classesDefinedInSameJar == null) {
        // TODO: Library used in this project (e.g., Guava) interferes the jar files of the class
        // Fix the interference
        return Collections.emptyList();
      }
      List<FullyQualifiedMethodSignature> nextMethodReferences =
          ClassDumper.listMethodReferences(javaClass);
      List<FullyQualifiedMethodSignature> nextExternalMethodReferences = new ArrayList<>();
      for (FullyQualifiedMethodSignature methodReference : nextMethodReferences) {
        String classNameInMethodReference = methodReference.getClassName();
        if (!className.equals(classNameInMethodReference)
            && !classesDefinedInSameJar.contains(classNameInMethodReference)) {
          nextExternalMethodReferences.add(methodReference);
        }
      }
      return nextExternalMethodReferences;
    } catch (ClassNotFoundException | URISyntaxException ex) {
      try {
        Class k = ClassDumper.class.getClassLoader().loadClass(className);
        System.out.println("Class was loaded in this class loader: " + k);
      } catch (ClassNotFoundException e) {
        System.out.println("Couldn't get class" + className);
        e.printStackTrace();
      }
      throw new RuntimeException(
          "There was an error in reading method references from the class: " + className, ex);
    }
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
