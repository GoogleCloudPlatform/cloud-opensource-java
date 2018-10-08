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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.InnerClass;
import org.apache.bcel.classfile.InnerClasses;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.SyntheticRepository;

/**
 * This class reads jar files and run static linkage analysis among them
 */
class StaticLinkageChecker {

  /**
   * Given list of the jar files as file names in filesystem, outputs the report of static linkage
   * check.
   *
   * @param arguments jar files available in the file system
   * @throws IOException when there is a problem in reading a jar file
   * @throws ClassNotFoundException when there is a problem in reading a class from a jar file
   */
  public static void main(String[] arguments) throws IOException, ClassNotFoundException {
    String report = generateStaticLinkageReport(arguments);
    System.out.println(report);
  }

  @VisibleForTesting
  static String generateStaticLinkageReport(String[] jarFileNames) throws IOException,
      ClassNotFoundException {
    StringBuilder stringBuilder = new StringBuilder();
    Set<FullyQualifiedMethodSignature> externalMethodReferences = new HashSet<>();
    List<Path> paths = new ArrayList<>();
    for (String jarFileName : jarFileNames) {
      File jarFile = new File(jarFileName);
      Path jarFilePath = jarFile.toPath();
      paths.add(jarFilePath);
      externalMethodReferences.addAll(listExternalMethodReferences(jarFilePath));
    }
    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        resolvedMethodReferences(paths, Lists.newArrayList(externalMethodReferences));
    if (unresolvedMethodReferences.isEmpty()) {
      stringBuilder.append("There was no unresolved method references from the jar file(s) :");
      stringBuilder.append(paths);
    } else {
      stringBuilder.append("There was unresolved method references from the jar file(s):\n");
      for (FullyQualifiedMethodSignature methodReference : unresolvedMethodReferences) {
        stringBuilder.append("  ");
        stringBuilder.append(methodReference);
        stringBuilder.append("\n");
      }
    }
    return stringBuilder.toString();
  }

  /**
   * Checks the availability of the methods through the jar files, and lists the unavailable
   * methods.
   *
   * @param paths jar files to search for the availability of the methods
   * @param methodReferences methods to search for with the jar files
   * @return list of methods that are not found in the jar files
   */
  static List<FullyQualifiedMethodSignature> resolvedMethodReferences(List<Path> paths,
      List<FullyQualifiedMethodSignature> methodReferences) {
    List<FullyQualifiedMethodSignature> unresolvedMethods = new ArrayList<>();

    // Creates chain of ClassPath element in the same order as paths
    ClassPath classPath = null;
    for (Path path : paths) {
      String pathFileName = path.toFile().getAbsolutePath();
      classPath = new ClassPath(classPath, pathFileName);
    }
    SyntheticRepository repository = SyntheticRepository.getInstance(classPath);

    for (FullyQualifiedMethodSignature methodReference : methodReferences) {
      try {
        JavaClass javaClass = repository.loadClass(methodReference.getClassName());
        MethodSignature methodSignature = methodReference.getMethodSignature();
        boolean methodFound = false;
        // Opportunity to get better performance to create a cache (from class name to method list)
        for (Method method : javaClass.getMethods()) {
          String signature = method.getSignature();
          if (method.getName().equals(methodSignature.getMethodName())
              && signature.equals(methodSignature.getDescriptor())) {
            methodFound = true;
            break;
          }
        }
        if (! methodFound) {
          unresolvedMethods.add(methodReference);
        }
      } catch (ClassNotFoundException ex) {
        unresolvedMethods.add(methodReference);
      }
    }
    return unresolvedMethods;
  }

  /**
   * Lists all external methods called from the classes in the jar file. The output list does not
   * include the methods defined in the file.
   *
   * @param jarFilePath the jar file to analyze
   * @return list of the method signatures with their fully-qualified classes
   * @throws IOException when there is a problem in reading the jar file
   * @throws ClassNotFoundException when a class visible by Guava's reflect was unexpectedly not
   *     found by BCEL API
   */
  static List<FullyQualifiedMethodSignature> listExternalMethodReferences(
      Path jarFilePath) throws IOException, ClassNotFoundException {
    List<FullyQualifiedMethodSignature> methodReferences = new ArrayList<>();
    Set<String> internalClassNames = new HashSet<>();

    String fileName = jarFilePath.toFile().getAbsolutePath();
    SyntheticRepository repository = SyntheticRepository.getInstance(new ClassPath(fileName));

    URL jarFileUrl = jarFilePath.toUri().toURL();
    for (ClassInfo classInfo : listTopLevelClassesFromJar(jarFileUrl)) {
      String className = classInfo.getName();
      JavaClass javaClass = repository.loadClass(className);
      String topLevelClassName = javaClass.getClassName();
      internalClassNames.add(topLevelClassName);
      internalClassNames.addAll(listInnerClassNames(javaClass));
      methodReferences.addAll(ClassDumper.listMethodReferences(javaClass));
    }

    List<FullyQualifiedMethodSignature> externalMethodReferences = methodReferences.stream()
        .filter(reference -> ! internalClassNames.contains(reference.getClassName()))
        .collect(Collectors.toList());
    return externalMethodReferences;
  }

  @VisibleForTesting
  static Set<String> listInnerClassNames(JavaClass javaClass) {
    Set<String> innerClassNames = new HashSet<>();
    String topLevelClassName = javaClass.getClassName();
    Attribute[] attributes = javaClass.getAttributes();
    ConstantPool constantPool = javaClass.getConstantPool();
    for (Attribute attribute : attributes) {
      if (attribute.getTag() != Const.ATTR_INNER_CLASSES) {
        continue;
      }
      InnerClasses innerClasses = (InnerClasses) attribute;
      for (InnerClass innerClass : innerClasses.getInnerClasses()) {
        int classIndex = innerClass.getInnerClassIndex();
        String innerClassName = constantPool.getConstantString(classIndex, Const.CONSTANT_Class);
        int outerClassIndex = innerClass.getOuterClassIndex();
        if (outerClassIndex > 0) {
          String outerClassName = constantPool.getConstantString(outerClassIndex,
              Const.CONSTANT_Class);
          String normalOuterClassName = outerClassName.replace('/', '.');
          if (! normalOuterClassName.equals(topLevelClassName)) {
            continue;
          }
        }

        // Class names stored in constant pool have '/' as separator. We want '.' (as binary name)
        String normalInnerClassName = innerClassName.replace('/', '.');
        innerClassNames.add(normalInnerClassName);
      }
    }
    return innerClassNames;
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
}
