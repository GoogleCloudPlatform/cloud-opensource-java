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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

/**
 * This class reads Java class file to analyze following attributes:
 *
 * <ol>
 *   <li>source (defined methods) via Method fields in the class, and</li>
 *   <li>targets (what's attempted to be invoked) via ConstantPool field of the class</li>
 * </ol>
 */
class ClassDumper {

  private static List<ConstantPoolMethodref> listConstantPoolMethodRef(JavaClass javaClass) {
    List<ConstantPoolMethodref> constantPoolMethodrefs = new ArrayList<>();
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
      Constant constantNameAndTypeRaw = constantPool.getConstant(nameAndTypeIndex);
      if (!(constantNameAndTypeRaw instanceof ConstantNameAndType)) {
        throw new RuntimeException(
            "Failed to lookup nameAndType constant indexed " + nameAndTypeIndex);
      }
      ConstantNameAndType constantNameAndType = (ConstantNameAndType) constantNameAndTypeRaw;
      String methodName = constantNameAndType.getName(constantPool);
      String signature = constantNameAndType.getSignature(constantPool);
      ConstantPoolMethodref methodref = new ConstantPoolMethodref(className, methodName, signature);
      constantPoolMethodrefs.add(methodref);
    }
    return constantPoolMethodrefs;
  }

  /**
   *  Lists all methodref entries defined in ConstantPool of class file
   *
   * @param classFileStream stream of a class file
   * @param fileName name of the file that contains class
   * @return List of all methodref entries defined in ConstantPool
   * @throws IOException when there is problem in reading classFileStream
   */
  @VisibleForTesting
  static List<ConstantPoolMethodref> listConstantPoolMethodref(InputStream classFileStream,
      String fileName) throws IOException {
    ClassParser parser = new ClassParser(classFileStream, fileName);
    JavaClass javaClass = parser.parse();
    return listConstantPoolMethodRef(javaClass);
  }

  /**
   *  Lists the methodref entries owned by the defined class in class file
   *
   * @param classFileStream stream of a class file
   * @param fileName name of the file that contains class
   * @return methodref entries owned by the class
   * @throws IOException when there is problem in reading classFileStream
   */
  public static List<ConstantPoolMethodref> listOwningConstantPoolMethodref(InputStream classFileStream,
      String fileName) throws IOException {
    ClassParser parser = new ClassParser(classFileStream, fileName);
    JavaClass javaClass = parser.parse();
    List<ConstantPoolMethodref> methodrefs = listConstantPoolMethodRef(javaClass);
    Set<String> owningClass = Sets.newHashSet(javaClass.getClassName());
    List<ConstantPoolMethodref> owningMethodrefs =  methodrefs
            .stream()
            .filter(methodref -> owningClass.contains(methodref.getClassName()))
            .collect(Collectors.toList());
    return owningMethodrefs;
  }

  /**
   *  Lists the methodref entries not owned by the defined class in class file
   *  The list the class attemts to invoke the methods defined elsewhere
   *
   * @param classFileStream stream of a class file
   * @param fileName name of the file that contains class
   * @return methodref entries external to the class
   * @throws IOException when there is problem in reading classFileStream
   */
  public static List<ConstantPoolMethodref> listExternalConstantPoolMethodref(
      InputStream classFileStream, String fileName) throws IOException {
    ClassParser parser = new ClassParser(classFileStream, fileName);
    JavaClass javaClass = parser.parse();
    List<ConstantPoolMethodref> methodrefs = listConstantPoolMethodRef(javaClass);
    Set<String> owningClass = Sets.newHashSet(javaClass.getClassName());
    List<ConstantPoolMethodref> owningMethodrefs =
        methodrefs
            .stream()
            .filter(methodref -> !owningClass.contains(methodref.getClassName()))
            .collect(Collectors.toList());
    return owningMethodrefs;
  }

  /**
   * Lists methods entries defined in the class file
   *
   * @param classFileStream stream of a class file
   * @param fileName name of the file that contains class
   * @return method and signature entries defined in the class file
   * @throws IOException when there is problem in reading classFileStream
   */
  public static List<MethodAndSignature> listDeclaredMethods(InputStream classFileStream, String fileName)
      throws IOException {
    final ClassParser parser = new ClassParser(classFileStream, fileName);
    final JavaClass javaClass = parser.parse();
    Method[] methods = javaClass.getMethods();

    List<MethodAndSignature> signatures = Arrays.stream(methods)
        .map(method -> new MethodAndSignature(method.getName(), method.getSignature()))
        .collect(Collectors.toList());
    return signatures;
  }

  /**
   * Lists the content of ConstantPool in a class file
   *
   * @param inputStream stream of a class file
   * @param fileName name of the file that contains class
   * @return String representation of ConstantPool entries
   * @throws IOException when there is problem in reading classFileStream
   */
  @VisibleForTesting
  static List<String> listConstantPool(InputStream inputStream, String fileName)
      throws IOException {
    final ClassParser parser = new ClassParser(inputStream, fileName);
    final JavaClass javaClass = parser.parse();
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
