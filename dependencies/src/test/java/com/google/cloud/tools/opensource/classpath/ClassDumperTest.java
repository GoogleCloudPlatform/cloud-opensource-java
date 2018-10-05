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

import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClassDumperTest {

  // We're sure that FirestoreGrpc class comes from this class file because
  // this project (cloud-opensource-java) doesn't have dependency for Cloud Firestore
  private final String EXAMPLE_CLASS_FILE = "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";
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
  public void testListConstantPool() throws IOException {
    List<String> constantPool = ClassDumper.listConstantPool(classFileInputStream,
        EXAMPLE_CLASS_FILE);
    Truth.assertThat(constantPool).hasSize(491);
  }

  @Test
  public void testListMethodReferences() throws IOException {
    List<FullyQualifiedMethodSignature> methodrefs =
        ClassDumper.listMethodReferences(classFileInputStream, EXAMPLE_CLASS_FILE);

    Truth.assertThat(methodrefs).hasSize(56);
    FullyQualifiedMethodSignature methodCallToListenRequest =
        new FullyQualifiedMethodSignature(
            "com.google.firestore.v1beta1.ListenRequest",
            "getDefaultInstance",
            "()Lcom/google/firestore/v1beta1/ListenRequest;");
    Truth.assertThat(methodrefs).contains(methodCallToListenRequest);
  }

  @Test
  public void testListInternalMethodReferences() throws IOException {
    List<FullyQualifiedMethodSignature> owningMethodrefs =
        ClassDumper.listInternalMethodReferences(classFileInputStream, EXAMPLE_CLASS_FILE);

    Truth.assertThat(owningMethodrefs).hasSize(13);
    for (FullyQualifiedMethodSignature methodref : owningMethodrefs) {
      Assert.assertEquals(
          "All of the methods here should be defined in this files",
          "com.google.firestore.v1beta1.FirestoreGrpc",
          methodref.getClassName());
    }
  }

  @Test
  public void testListExternalMethodReferences() throws IOException {
    List<FullyQualifiedMethodSignature> externalMethodrefs =
        ClassDumper.listExternalMethodReferences(classFileInputStream, EXAMPLE_CLASS_FILE);

    Truth.assertThat(externalMethodrefs).hasSize(43);
    for (FullyQualifiedMethodSignature methodref : externalMethodrefs) {
      Assert.assertNotEquals(
          "All of the methods here should be defined in other files",
          "com.google.firestore.v1beta1.FirestoreGrpc",
          methodref.getClassName());
    }
    FullyQualifiedMethodSignature methodCallToListenRequest =
        new FullyQualifiedMethodSignature(
            "com.google.firestore.v1beta1.ListenRequest",
            "getDefaultInstance",
            "()Lcom/google/firestore/v1beta1/ListenRequest;");
    Truth.assertThat(externalMethodrefs).contains(methodCallToListenRequest);
  }

  @Test
  public void testListDeclaredMethods() throws IOException {
    List<MethodSignature> signatures = ClassDumper.listDeclaredMethods(classFileInputStream,
        EXAMPLE_CLASS_FILE);

    Truth.assertThat(signatures).hasSize(45);

    // getRunQueryMethod is a method defined in FirestoreGrpc class
    // Because of type erasure, type parameters don't appear in signature of BCEL Method class
    MethodSignature oneExpectedMethodInClass = new MethodSignature("getRunQueryMethod",
        "()Lio/grpc/MethodDescriptor;"); // No type parameter expected
    Truth.assertThat(signatures).contains(oneExpectedMethodInClass);
  }
}
