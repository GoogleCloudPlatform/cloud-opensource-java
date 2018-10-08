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
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
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
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.SyntheticRepository;

/**
 * This class reads a jar file and list method references in its class files
 */
class JarDumper {

  /**
   *  Lists all external method references from the jar file.
   *
   * @param jarFileUrl URL for the jar file
   * @return list of the method signatures with their fully-qualified classes
   * @throws IOException when there is a problem in reading the jar file
   */
  public static List<FullyQualifiedMethodSignature> listExternalMethodReferences(
      URL jarFileUrl) throws IOException {
    List<FullyQualifiedMethodSignature> methodReferences = new ArrayList<>();

    SyntheticRepository repository = SyntheticRepository.getInstance(
        new ClassPath(jarFileUrl.getFile()));
    Set<String> internalClassNames = new HashSet<>();
    for (ClassInfo classInfo : listTopLevelClassesFromJar(jarFileUrl)) {
      String className = classInfo.getName();
      JavaClass javaClass;
      try {
        javaClass = repository.loadClass(className);
      } catch (ClassNotFoundException ex) {
        throw new RuntimeException(
            "A class visible by Guava's reflect was unexpectedly not found by BCEL API", ex);
      }
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
    Attribute[] attributes = javaClass.getAttributes();
    ConstantPool constantPool = javaClass.getConstantPool();
    for (Attribute attribute : attributes) {
      if (attribute.getTag() != Const.ATTR_INNER_CLASSES) {
        continue;
      }
      InnerClasses innerClasses = (InnerClasses) attribute;
      for (InnerClass innerClass : innerClasses.getInnerClasses()) {
        int classIndex = innerClass.getInnerClassIndex();
        String innerClassName =  constantPool.getConstantString(classIndex, Const.CONSTANT_Class);
        // Class names stored in constant pool have '/' as separator. We want '.'
        String binaryClassName = innerClassName.replace('/', '.');
        innerClassNames.add(binaryClassName);
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
    ImmutableSet<ClassInfo> allClassesInJar =  classPath.getTopLevelClasses();
    return allClassesInJar;
  }
}
