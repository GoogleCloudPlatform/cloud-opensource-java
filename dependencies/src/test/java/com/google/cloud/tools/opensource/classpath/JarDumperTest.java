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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.junit.Test;

public class JarDumperTest {
  private final String EXAMPLE_JAR_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar";
  private final String EXAMPLE_CLASS_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";

  @Test
  public void testListExternalMethodReferences() throws IOException, ClassNotFoundException {
    URL jarFileUrl = URLClassLoader.getSystemResource(EXAMPLE_JAR_FILE);
    File jarFile = new File(jarFileUrl.getFile());
    List<FullyQualifiedMethodSignature> signatures =
        JarDumper.listExternalMethodReferences(jarFile);

    Truth.assertThat(signatures).hasSize(38);
    FullyQualifiedMethodSignature expectedExternalMethodReference =
        new FullyQualifiedMethodSignature(
            "io.grpc.protobuf.ProtoUtils",
            "marshaller",
            "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;");
    Truth.assertThat(signatures).contains(expectedExternalMethodReference);

    String classNameInJar = "com.google.firestore.v1beta1.FirestoreGrpc";
    for (FullyQualifiedMethodSignature methodReference : signatures) {
      Truth.assertThat(methodReference.getClassName()).doesNotContain(classNameInJar);
    }
  }

  @Test
  public void testListInnerClasses() throws IOException {
    InputStream classFileInputStream = URLClassLoader.getSystemResourceAsStream(EXAMPLE_CLASS_FILE);
    ClassParser parser = new ClassParser(classFileInputStream, EXAMPLE_CLASS_FILE);
    JavaClass javaClass = parser.parse();

    Set<String> innerClassNames = JarDumper.listInnerClassNames(javaClass);
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
}
