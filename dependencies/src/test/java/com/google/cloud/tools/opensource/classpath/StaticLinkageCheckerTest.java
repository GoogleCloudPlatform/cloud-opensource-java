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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.junit.Test;

public class StaticLinkageCheckerTest {
  private final String EXAMPLE_JAR_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar";
  private final String EXAMPLE_PROTO_JAR_FILE =
      "testdata/proto-google-cloud-firestore-v1beta1-0.28.0.jar";
  private final String EXAMPLE_CLASS_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";

  @Test
  public void testListExternalMethodReferences()
      throws IOException, ClassNotFoundException, URISyntaxException {
    URL jarFileUrl = URLClassLoader.getSystemResource(EXAMPLE_JAR_FILE);
    List<FullyQualifiedMethodSignature> signatures =
        StaticLinkageChecker.listExternalMethodReferences(Paths.get(jarFileUrl.toURI()));

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
    InputStream classFileInputStream = URLClassLoader.getSystemResourceAsStream(
        EXAMPLE_CLASS_FILE);
    ClassParser parser = new ClassParser(classFileInputStream, EXAMPLE_CLASS_FILE);
    JavaClass javaClass = parser.parse();

    Set<String> innerClassNames = StaticLinkageChecker.listInnerClassNames(javaClass);
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
  public void testResolvedMethodReferences() throws URISyntaxException {
    List<Path> pathsForJar = Arrays.asList(
        Paths.get(URLClassLoader.getSystemResource(EXAMPLE_JAR_FILE).toURI()),
        Paths.get(URLClassLoader.getSystemResource(EXAMPLE_PROTO_JAR_FILE).toURI()));
    FullyQualifiedMethodSignature internalMethodReference =
        new FullyQualifiedMethodSignature(
            "com.google.firestore.v1beta1.FirestoreGrpc",
            "getListCollectionIdsMethodHelper",
            "()Lio/grpc/MethodDescriptor;");

    FullyQualifiedMethodSignature externalMethodReference =
        new FullyQualifiedMethodSignature(
            "io.grpc.protobuf.ProtoUtils",
            "marshaller",
            "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;");

    List<FullyQualifiedMethodSignature> methodReferences = Arrays.asList(
        internalMethodReference, externalMethodReference
    );
    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        StaticLinkageChecker.resolvedMethodReferences(pathsForJar, methodReferences);
    Truth.assertThat(unresolvedMethodReferences).hasSize(1);
  }

  @Test
  public void testResolvedMethodReferencesWithJarFiles()
      throws IOException, ClassNotFoundException {
    String[] jarFileNames = new String[] {
        URLClassLoader.getSystemResource(EXAMPLE_JAR_FILE).getFile(),
        URLClassLoader.getSystemResource(EXAMPLE_PROTO_JAR_FILE).getFile()
    };

    String report = StaticLinkageChecker.generateStaticLinkageReport(jarFileNames);

    // com.google.protobuf.Int32Value is defined in protobuf-java-X.Y.Z.jar, not in the file list
    Truth.assertThat(report).contains(
        "FullyQualifiedMethodSignature{className=com.google.protobuf.Int32Value, methodSignature=MethodSignature{methodName=parser, descriptor=()Lcom/google/protobuf/Parser;}}"
    );
    // io.grpc.MethodDescriptor is defined in grpc-core-X.Y.Z.jar, not in the file list
    Truth.assertThat(report).contains(
        "FullyQualifiedMethodSignature{className=io.grpc.MethodDescriptor, methodSignature=MethodSignature{methodName=newBuilder, descriptor=()Lio/grpc/MethodDescriptor$Builder;}}"
    );
    // As RunQueryRequest is defined in the proto jar file, it should not appear in the report
    Truth.assertThat(report).doesNotContain(
        "com.google.firestore.v1beta1.RunQueryRequest"
    );
    // As FirestoreGrpc is defined in the example jar file, it should not appear in the report
    Truth.assertThat(report).doesNotContain(
        "com.google.firestore.v1beta1.FirestoreGrpc"
    );
  }
}
