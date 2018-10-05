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
  private String classFileName = "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";
  private InputStream classFileInputStream;

  @Before
  public void setup() {
    classFileInputStream = URLClassLoader.getSystemResourceAsStream(classFileName);
  }

  @After
  public void cleanup() throws IOException {
    classFileInputStream.close();
  }

  @Test
  public void listConstantPool() throws IOException {
    List<String> constantPool = ClassDumper.listConstantPool(classFileInputStream, classFileName);
    Truth.assertThat(constantPool).hasSize(491);
  }

  @Test
  public void testListMethodref() throws IOException {
    List<ConstantPoolMethodref> methodrefs =
        ClassDumper.listConstantPoolMethodref(classFileInputStream, classFileName);

    Truth.assertThat(methodrefs).hasSize(56);
    ConstantPoolMethodref methodCallToListenRequest =
        new ConstantPoolMethodref(
            "com.google.firestore.v1beta1.ListenRequest",
            "getDefaultInstance",
            "()Lcom/google/firestore/v1beta1/ListenRequest;");
    Truth.assertThat(methodrefs).contains(methodCallToListenRequest);
  }

  @Test
  public void testListOwningConstantPoolMethodref() throws IOException {
    List<ConstantPoolMethodref> owningMethodrefs =
        ClassDumper.listOwningConstantPoolMethodref(classFileInputStream, classFileName);

    Truth.assertThat(owningMethodrefs).hasSize(13);
    for (ConstantPoolMethodref methodref : owningMethodrefs) {
      Assert.assertEquals(
          "All of the methods here should be defined in this files",
          "com.google.firestore.v1beta1.FirestoreGrpc",
          methodref.getClassName());
    }
  }

  @Test
  public void testListExternalConstantPoolMethodref() throws IOException {
    List<ConstantPoolMethodref> externalMethodrefs =
        ClassDumper.listExternalConstantPoolMethodref(classFileInputStream, classFileName);

    Truth.assertThat(externalMethodrefs).hasSize(43);
    for (ConstantPoolMethodref methodref : externalMethodrefs) {
      Assert.assertNotEquals(
          "All of the methods here should be defined in other files",
          "com.google.firestore.v1beta1.FirestoreGrpc",
          methodref.getClassName());
    }
    ConstantPoolMethodref methodCallToListenRequest =
        new ConstantPoolMethodref(
            "com.google.firestore.v1beta1.ListenRequest",
            "getDefaultInstance",
            "()Lcom/google/firestore/v1beta1/ListenRequest;");
    Truth.assertThat(externalMethodrefs).contains(methodCallToListenRequest);
  }

  @Test
  public void testListDeclaredMethods() throws IOException {
    List<MethodAndSignature> signatures = ClassDumper.listDeclaredMethods(classFileInputStream, classFileName);

    Truth.assertThat(signatures).hasSize(45);

    // getRunQueryMethod is a method defined in FirestoreGrpc class
    // About type parameters:
    // While getRunQueryMethod's string representation contains type parameters:
    //   "public static io.grpc.MethodDescriptor getRunQueryMethod() [Signature: ()Lio/grpc/MethodDescriptor<Lcom/google/firestore/v1beta1/RunQueryRequest;Lcom/google/firestore/v1beta1/RunQueryResponse;>;][RuntimeInvisibleAnnotations]";
    // , these parameters don't appear in signature field of BCEL Method class
    MethodAndSignature oneExpectedMethodInClass =
        new MethodAndSignature(
            "getRunQueryMethod",
            "()Lio/grpc/MethodDescriptor;"); // No type parameter in signature field
    Truth.assertThat(signatures).contains(oneExpectedMethodInClass);
  }
}
