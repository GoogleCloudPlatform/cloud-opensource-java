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

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Set;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClassDumperTest {

  // We're sure that FirestoreGrpc class comes from this class file because
  // this project (cloud-opensource-java) doesn't have dependency for Cloud Firestore
  private static final String EXAMPLE_CLASS_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";
  private InputStream classFileInputStream;

  @Before
  public void setup() {
    classFileInputStream = URLClassLoader.getSystemResourceAsStream(EXAMPLE_CLASS_FILE);
  }

  @After
  public void cleanup() throws IOException {
    classFileInputStream.close();
  }

  @Test
  public void testMethodDescriptorToClass_byteArray() {
    ClassDumper classDumper = ClassDumper.create(ImmutableList.of());
    Class[] byteArrayClass =
        classDumper.methodDescriptorToClass("([B)Ljava/lang/String;");
    Assert.assertTrue(byteArrayClass[0].isArray());
  }

  @Test
  public void testMethodDescriptorToClass_primitiveTypes() {
    ClassDumper classDumper = ClassDumper.create(ImmutableList.of());
    // List of primitive types that appear in descriptor:
    // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3
    Class[] types =
        classDumper.methodDescriptorToClass("(BCDFIJSZ)Ljava/lang/String;");
    Truth.assertThat(types)
        .asList()
        .containsExactly(
            byte.class,
            char.class,
            double.class,
            float.class,
            int.class,
            long.class,
            short.class,
            boolean.class)
        .inOrder();
  }

  @Test
  public void testMethodDefinitionExists_arrayType() throws ClassNotFoundException {
    FullyQualifiedMethodSignature checkArgumentMethod =
        new FullyQualifiedMethodSignature(
            "com.google.common.base.Preconditions", "checkArgument", "(ZLjava/lang/Object;)V");
    ClassDumper classDumper = ClassDumper.create(ImmutableList.of());

    Assert.assertTrue(classDumper.methodDefinitionExists(checkArgumentMethod));
  }

  @Test
  public void testMethodDefinitionExists_constructorInAbstractClass()
      throws ClassNotFoundException {
    ClassDumper classDumper = ClassDumper.create(ImmutableList.of());
    FullyQualifiedMethodSignature constructorInAbstract =
        new FullyQualifiedMethodSignature(
            "com.google.common.collect.LinkedHashMultimapGwtSerializationDependencies",
            "<init>",
            "(Ljava/util/Map;)V");

    Assert.assertTrue(classDumper.methodDefinitionExists(constructorInAbstract));
  }

  @Test
  public void testListInnerClasses() throws IOException {
    InputStream classFileInputStream = URLClassLoader.getSystemResourceAsStream(
        EXAMPLE_CLASS_FILE);
    ClassParser parser = new ClassParser(classFileInputStream, EXAMPLE_CLASS_FILE);
    JavaClass javaClass = parser.parse();

    Set<String> innerClassNames = ClassDumper.listInnerClassNames(javaClass);
    Truth.assertThat(innerClassNames).containsExactly(
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreFutureStub",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreMethodDescriptorSupplier",
        "com.google.firestore.v1beta1.FirestoreGrpc$1",
        "com.google.firestore.v1beta1.FirestoreGrpc$MethodHandlers",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreStub",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreBaseDescriptorSupplier",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreBlockingStub",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreImplBase",
        "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreFileDescriptorSupplier"
    );
  }

  @Test
  public void testCreationInvalidInput() {
    try {
      ClassDumper.create(ImmutableList.of(Paths.get("")));
      Assert.fail("Empty path should generate RuntimeException");
    } catch (RuntimeException ex) {
      Assert.assertEquals("There was problem in loading classes in jar file", ex.getMessage());
    }
  }
}
