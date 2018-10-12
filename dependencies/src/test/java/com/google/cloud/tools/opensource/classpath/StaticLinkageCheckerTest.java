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

import com.google.common.collect.Lists;
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
import org.apache.bcel.util.SyntheticRepository;
import org.eclipse.aether.RepositoryException;
import org.junit.Assert;
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
    List<Path> pathsForJar = Lists.newArrayList(
        absolutePathOfResource(EXAMPLE_JAR_FILE),
        absolutePathOfResource(EXAMPLE_PROTO_JAR_FILE));
    List<Path> firestoreDependencies = Arrays.asList(
        absolutePathOfResource("testdata/protobuf-java-3.6.1.jar"),
        absolutePathOfResource("testdata/grpc-core-1.13.1.jar"),
        absolutePathOfResource("testdata/grpc-stub-1.13.1.jar"),
        absolutePathOfResource("testdata/grpc-protobuf-1.13.1.jar")
    );
    pathsForJar.addAll(firestoreDependencies);

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
        StaticLinkageChecker.findUnresolvedReferences(pathsForJar, methodReferences);
    Truth.assertThat(unresolvedMethodReferences).hasSize(1);
  }

  private Path absolutePathOfResource(String resourceName) throws URISyntaxException {
    return Paths.get(URLClassLoader.getSystemResource(resourceName).toURI()).toAbsolutePath();
  }

  @Test
  public void testResolvedMethodReferencesWithJarFiles()
      throws IOException, ClassNotFoundException, URISyntaxException {
    List<Path> pathsForJar = Lists.newArrayList(
        absolutePathOfResource(EXAMPLE_JAR_FILE),
        absolutePathOfResource(EXAMPLE_PROTO_JAR_FILE)
    );
    List<Path> firestoreDependencies = Arrays.asList(
        absolutePathOfResource("testdata/protobuf-java-3.6.1.jar"),
        absolutePathOfResource("testdata/grpc-core-1.13.1.jar"),
        absolutePathOfResource("testdata/grpc-stub-1.13.1.jar"),
        absolutePathOfResource("testdata/grpc-protobuf-1.13.1.jar")
    );
    pathsForJar.addAll(firestoreDependencies);


    List<FullyQualifiedMethodSignature> report =
        StaticLinkageChecker.findUnresolvedMethodReferences(pathsForJar);
    FullyQualifiedMethodSignature methodExpectedToBeUnresolved =
        new FullyQualifiedMethodSignature(
            "com.google.api.pathtemplate.PathTemplate",
            "instantiate",
            "([Ljava/lang/String;)Ljava/lang/String;");
    Truth.assertThat(report).contains(methodExpectedToBeUnresolved);
    // As RunQueryRequest is defined in the proto jar file, it should not appear in the report
    Truth.assertThat(report.toString())
        .doesNotContain("com.google.firestore.v1beta1.RunQueryRequest");
    // As FirestoreGrpc is defined in the example jar file, it should not appear in the report
    Truth.assertThat(report.toString())
        .doesNotContain("com.google.firestore.v1beta1.FirestoreGrpc");
  }

  @Test
  public void testJarPathOrderInResolvingReferences() throws URISyntaxException {
    // listDocuments method on CollectionReference class is added at version 0.66.0-beta
    // https://github.com/googleapis/google-cloud-java/releases/tag/v0.66.0
    List<Path> firestoreDependencies = Lists.newArrayList(
        absolutePathOfResource("testdata/gax-1.32.0.jar"),
        absolutePathOfResource("testdata/api-common-1.7.0.jar"),
        absolutePathOfResource("testdata/google-cloud-core-1.48.0.jar"),
        absolutePathOfResource("testdata/google-cloud-core-grpc-1.48.0.jar"));

    List<Path> pathsForJarWithVersion65First =
        Lists.newArrayList(
            absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar"),
            absolutePathOfResource("testdata/google-cloud-firestore-0.66.0-beta.jar"));
    pathsForJarWithVersion65First.addAll(firestoreDependencies);

    List<Path> pathsForJarWithVersion66First =
        Lists.newArrayList(
            absolutePathOfResource("testdata/google-cloud-firestore-0.66.0-beta.jar"),
            absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar"));
    pathsForJarWithVersion66First.addAll(firestoreDependencies);

    FullyQualifiedMethodSignature methodAddedInVersion66 =
        new FullyQualifiedMethodSignature(
            "com.google.cloud.firestore.CollectionReference",
            "listDocuments",
            "()Ljava/lang/Iterable;");

    // When version 65 (old) comes first in the jar list, it cannot find listDocuments method
    List<FullyQualifiedMethodSignature> unresolvedMethodReferencesWithPath1 =
        StaticLinkageChecker.findUnresolvedReferences(pathsForJarWithVersion65First,
            Arrays.asList(methodAddedInVersion66));
    Truth.assertThat(unresolvedMethodReferencesWithPath1).hasSize(1);

    // When version 66 (new) comes first, it finds the method correctly
    List<FullyQualifiedMethodSignature> unresolvedMethodReferencesWithPath2 =
        StaticLinkageChecker.findUnresolvedReferences(pathsForJarWithVersion66First,
            Arrays.asList(methodAddedInVersion66));
    Truth.assertThat(unresolvedMethodReferencesWithPath2).hasSize(0);
  }

  @Test
  public void testNonExistentJarFileInput() throws ClassNotFoundException {
    try {
      StaticLinkageChecker.findUnresolvedMethodReferences(
          Arrays.asList(Paths.get("nosuchfile.jar")));
      Assert.fail("findUnresolvedMethodReferences should raise IOException");
    } catch (IOException ex) {
      Assert.assertEquals("The file is not readable: nosuchfile.jar", ex.getMessage());
    }
  }

  @Test
  public void testCoordinateToJarPaths_validCoordinate() throws RepositoryException {
    List<Path> paths = StaticLinkageChecker.coordinateToJarPaths("io.grpc:grpc-auth:1.15.1");
    Truth.assertThat(paths).hasSize(11);

    String pathsString = paths.toString();

    Truth.assertThat(pathsString).contains("io/grpc/grpc-auth/1.15.1/grpc-auth-1.15.1.jar");
    Truth.assertThat(pathsString)
        .contains(
            "com/google/auth/google-auth-library-credentials/0.9.0/google-auth-library-credentials-0.9.0.jar");
    paths.forEach(
        path -> {
          Truth.assertWithMessage("Every returned path should be an absolute path")
              .that(path.toString())
              .startsWith("/");
        });
  }

  @Test
  public void testCoordinateToJarPaths_invalidCoordinate() {
    try {
      StaticLinkageChecker.coordinateToJarPaths("io.grpc:nosuchartifact:1.2.3");
      Assert.fail("Invalid Maven coodinate should raise RepositoryException");
    } catch (RepositoryException ex) {
      Truth.assertThat(ex.getMessage())
          .contains("Could not find artifact io.grpc:nosuchartifact:jar:1.2.3");
    }
  }

  @Test
  public void testInheritanceWithGuavaCollectionInheritance() throws URISyntaxException {
    FullyQualifiedMethodSignature guavaCollectionPut =
        new FullyQualifiedMethodSignature(
            "com.google.common.collect.ArrayListMultimapGwtSerializationDependencies",
            "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z");
    List<Path> pathsForJar =
        Arrays.asList(
            Paths.get(URLClassLoader.getSystemResource("testdata/guava-26.0-jre.jar").toURI()));
    List<FullyQualifiedMethodSignature> methodsNotFound =
        StaticLinkageChecker.findUnresolvedReferences(
            pathsForJar, Arrays.asList(guavaCollectionPut));
    Truth.assertThat(methodsNotFound).hasSize(0);
  }

  @Test
  public void testArrayCloneMethod() throws URISyntaxException {
    FullyQualifiedMethodSignature arrayCloneMethod =
        new FullyQualifiedMethodSignature(
            "[Lio.grpc.InternalKnownTransport;", "clone", "()Ljava/lang/Object");
    List<Path> pathsForJar =
        Arrays.asList(
            Paths.get(URLClassLoader.getSystemResource("testdata/guava-26.0-jre.jar").toURI()));
    List<FullyQualifiedMethodSignature> methodsNotFound =
        StaticLinkageChecker.findUnresolvedReferences(pathsForJar, Arrays.asList(arrayCloneMethod));
    Truth.assertThat(methodsNotFound).hasSize(0);
  }

  @Test
  public void testMethodDefinitionExists_arrayType() throws ClassNotFoundException {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    FullyQualifiedMethodSignature checkArgumentMethod =
        new FullyQualifiedMethodSignature(
            "com.google.common.base.Preconditions",
            "checkArgument", "(ZLjava/lang/Object;)V");
    boolean exist =
        StaticLinkageChecker.methodDefinitionExists(
            checkArgumentMethod, classLoader, SyntheticRepository.getInstance());
    Assert.assertTrue(exist);
  }

  @Test
  public void testMethodDefinitionExists_constructorInAbstractClass()
      throws ClassNotFoundException {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    FullyQualifiedMethodSignature constructorInAbstract =
        new FullyQualifiedMethodSignature(
            "com.google.common.collect.LinkedHashMultimapGwtSerializationDependencies",
            "<init>",
            "(Ljava/util/Map;)V");
    boolean exist =
        StaticLinkageChecker.methodDefinitionExists(
            constructorInAbstract, classLoader, SyntheticRepository.getInstance());
    Assert.assertTrue(exist);
  }

  @Test
  public void testMethodDescriptorToClass_byteArray() {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    Class[] byteArrayClass =
        StaticLinkageChecker.methodDescriptorToClass("([B)Ljava/lang/String;", classLoader);
    Assert.assertTrue(byteArrayClass[0].isArray());
  }
}
