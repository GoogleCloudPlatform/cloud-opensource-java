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

import static org.hamcrest.CoreMatchers.is;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.SyntheticRepository;
import org.eclipse.aether.RepositoryException;
import org.junit.Assert;
import org.junit.Test;

public class StaticLinkageCheckerTest {
  private static final String EXAMPLE_JAR_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar";
  private static final String EXAMPLE_PROTO_JAR_FILE =
      "testdata/proto-google-cloud-firestore-v1beta1-0.28.0.jar";
  private static final String EXAMPLE_CLASS_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";
  private  static final ImmutableList<Path> FIRESTORE_DEPENDENCIES = ImmutableList.of(
      absolutePathOfResource("testdata/protobuf-java-3.6.1.jar"),
      absolutePathOfResource("testdata/grpc-core-1.13.1.jar"),
      absolutePathOfResource("testdata/grpc-stub-1.13.1.jar"),
      absolutePathOfResource("testdata/grpc-protobuf-1.13.1.jar"),
      absolutePathOfResource("testdata/grpc-protobuf-lite-1.13.1.jar")
  );

  private static final Correspondence<FullyQualifiedMethodSignature, String> CLASS_NAMES =
      new Correspondence<FullyQualifiedMethodSignature, String>() {
        @Override
        public boolean compare(FullyQualifiedMethodSignature actual, String expected) {
          return actual.getClassName().equals(expected);
        }
        @Override
        public String toString() {
          return "has class name equal to";
        }
      };

  @Test
  public void testListExternalMethodReferences()
      throws IOException, ClassNotFoundException, URISyntaxException {
    URL jarFileUrl = URLClassLoader.getSystemResource(EXAMPLE_JAR_FILE);
    List<FullyQualifiedMethodSignature> signatures =
        StaticLinkageChecker.listExternalMethodReferences(
            Paths.get(jarFileUrl.toURI()), new HashSet<>());

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
  public void testResolvedMethodReferences() {
    List<Path> pathsForJar = Lists.newArrayList(
        absolutePathOfResource(EXAMPLE_JAR_FILE),
        absolutePathOfResource(EXAMPLE_PROTO_JAR_FILE));
    pathsForJar.addAll(FIRESTORE_DEPENDENCIES);

    FullyQualifiedMethodSignature internalMethodReference =
        new FullyQualifiedMethodSignature(
            "com.google.firestore.v1beta1.FirestoreGrpc",
            "getListCollectionIdsMethodHelper",
            "()Lio/grpc/MethodDescriptor;");

    FullyQualifiedMethodSignature undefinedMethodReference =
        new FullyQualifiedMethodSignature(
            "dummy.ProtoUtils",
            "marshaller",
            "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;");

    List<FullyQualifiedMethodSignature> methodReferences = Arrays.asList(
        internalMethodReference, undefinedMethodReference
    );

    // findUnresolvedReferences does not follow references from this set
    Set<String> checkedClasses = Sets.newHashSet("com.google.firestore.v1beta1.FirestoreGrpc");
    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        StaticLinkageChecker.findUnresolvedReferences(
            pathsForJar, methodReferences, checkedClasses);
    Truth.assertThat(unresolvedMethodReferences).hasSize(1);
  }

  private static Path absolutePathOfResource(String resourceName) {
    try {
      return Paths.get(URLClassLoader.getSystemResource(resourceName).toURI()).toAbsolutePath();
    } catch (URISyntaxException ex) {
      throw new RuntimeException("Could not create URI for the files in resources directory");
    }
  }

  @Test
  public void testResolvedMethodReferencesWithJarFiles()
      throws IOException, ClassNotFoundException {
    List<Path> pathsForJar = Lists.newArrayList(
        absolutePathOfResource(EXAMPLE_JAR_FILE),
        absolutePathOfResource(EXAMPLE_PROTO_JAR_FILE)
    );
    pathsForJar.addAll(FIRESTORE_DEPENDENCIES);

    StaticLinkageChecker staticLinkageChecker = new StaticLinkageChecker(true, pathsForJar);
    List<FullyQualifiedMethodSignature> report =
        staticLinkageChecker.findUnresolvedMethodReferences();
    Truth.assertThat(report.toString()).doesNotContain("com.google.api.pathtemplate.PathTemplate");
    // As RunQueryRequest is defined in the proto jar file, it should not appear in the report
    Truth.assertThat(report.toString())
        .doesNotContain("com.google.firestore.v1beta1.RunQueryRequest");
    // As FirestoreGrpc is defined in the example jar file, it should not appear in the report
    Truth.assertThat(report.toString())
        .doesNotContain("com.google.firestore.v1beta1.FirestoreGrpc");
  }

  @Test
  public void testJarPathOrderInResolvingReferences() {
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
            Arrays.asList(methodAddedInVersion66), new HashSet<>());
    Truth.assertThat(unresolvedMethodReferencesWithPath1).hasSize(1);

    // When version 66 (new) comes first, it finds the method correctly
    List<FullyQualifiedMethodSignature> unresolvedMethodReferencesWithPath2 =
        StaticLinkageChecker.findUnresolvedReferences(pathsForJarWithVersion66First,
            Arrays.asList(methodAddedInVersion66), new HashSet<>());
    Truth.assertThat(unresolvedMethodReferencesWithPath2).doesNotContain(methodAddedInVersion66);
  }

  @Test
  public void testFindUnresolvedReferences_packagePrivateInnerClass()
      throws RepositoryException {
    List<Path> paths = StaticLinkageChecker.coordinateToClasspath("io.grpc:grpc-auth:1.15.1");

    FullyQualifiedMethodSignature constructorOfPrivateInnerClass =
        new FullyQualifiedMethodSignature(
            "io.opencensus.stats.View$AggregationWindow$Interval",
            "<init>",
            "()V");

    List<FullyQualifiedMethodSignature> unresolvedReferences = StaticLinkageChecker
        .findUnresolvedReferences(paths,
            Arrays.asList(constructorOfPrivateInnerClass), new HashSet<>());
    Truth.assertThat(unresolvedReferences).isEmpty();
  }

  @Test
  public void testNonExistentJarFileInput() throws ClassNotFoundException {
    try {
      StaticLinkageChecker staticLinkageChecker = new StaticLinkageChecker(true,
          ImmutableList.of(Paths.get("nosuchfile.jar")));
      staticLinkageChecker.findUnresolvedMethodReferences();
      Assert.fail("findUnresolvedMethodReferences should raise IOException");
    } catch (IOException ex) {
      Assert.assertEquals("The file is not readable: nosuchfile.jar", ex.getMessage());
    }
  }

  @Test
  public void testCoordinateToClasspath_validCoordinate() throws RepositoryException {
    List<Path> paths = StaticLinkageChecker.coordinateToClasspath("io.grpc:grpc-auth:1.15.1");
    Truth.assertThat(paths).hasSize(12);

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
  public void testCoordinateToClasspath_optionalDependency() throws RepositoryException {
    List<Path> paths =
        StaticLinkageChecker.coordinateToClasspath(
            "com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha");

    // The tree from google-cloud-bigtable to log4j:
    //   com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha (optional: false)
    //    com.google.cloud:google-cloud-core:jar:1.48.0 (optional: false)
    //      com.google.http-client:google-http-client:jar:1.24.1 (optional: false)
    //        org.apache.httpcomponents:httpclient:jar:4.5.3 (optional: false)
    //          commons-logging:commons-logging:jar:1.2 (optional: false)
    //            log4j:log4j:jar:1.2.17 (optional: true)
    Assert.assertTrue(
        paths.stream().anyMatch(path -> path.getFileName().toString().startsWith("log4j-1.2")));
  }

  @Test
  public void testCoordinateToClasspath_invalidCoordinate() {
    try {
      StaticLinkageChecker.coordinateToClasspath("io.grpc:nosuchartifact:1.2.3");
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
            pathsForJar, Arrays.asList(guavaCollectionPut), new HashSet<>());
    Truth.assertThat(methodsNotFound).hasSize(0);
  }

  @Test
  public void testFindUnresolvedReferences_unusedLzmaClassByGrpc()
      throws RepositoryException, IOException, ClassNotFoundException {
    String bigTableCoordinate = "com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha";
    List<Path> paths = StaticLinkageChecker.coordinateToClasspath(bigTableCoordinate);
    Truth.assertThat(paths).isNotEmpty();

    // Prior to class usage graph traversal, there was linkage error for lzma-java classes.
    StaticLinkageChecker staticLinkageChecker = new StaticLinkageChecker(true, paths);
    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        staticLinkageChecker.findUnresolvedMethodReferences();

    Truth.assertWithMessage(
            "Because lzma-java classes are unreachable from google-cloud-bigtable (entry point),"
                + "the classes should not appear as unresolved method references.")
        .that(unresolvedMethodReferences)
        .isEmpty();
  }

  @Test
  public void testFindUnresolvedReferences_checkAllOption()
      throws RepositoryException, IOException, ClassNotFoundException {
    String bigTableCoordinate = "com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha";
    List<Path> paths = StaticLinkageChecker.coordinateToClasspath(bigTableCoordinate);

    // grpc-netty-shaded pom.xml does not have dependency to lzma-java even though netty-codec
    // refers lzma.sdk.lzma.Encoder. StaticLinkageChecker should be able to detect it.
    boolean reportOnlyReachable = false;
    StaticLinkageChecker staticLinkageChecker = new StaticLinkageChecker(reportOnlyReachable,
        paths);
    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        staticLinkageChecker.findUnresolvedMethodReferences();

    Truth.assertWithMessage(
            "StaticLinkageChecker.reportOnlyReachable should check all classes in the classpath")
        .that(unresolvedMethodReferences)
        .comparingElementsUsing(CLASS_NAMES)
        .contains("lzma.sdk.lzma.Encoder");
  }

  @Test
  public void testFindUnresolvedReferences_appengineSdkWithProvidedScope()
      throws RepositoryException, IOException, ClassNotFoundException {
    String bigTableCoordinate = "com.google.cloud:google-cloud-compute:jar:0.67.0-alpha";
    List<Path> paths = StaticLinkageChecker.coordinateToClasspath(bigTableCoordinate);

    // Prior to 'provided' scope inclusion, there was linkage error for classes in
    // com.google.appengine.api.urlfetch package.
    StaticLinkageChecker staticLinkageChecker = new StaticLinkageChecker(true,
        paths);
    List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
        staticLinkageChecker.findUnresolvedMethodReferences();

    Assert.assertThat(
        "Classes in com.google.appengine.api.urlfetch package are provided from appengine-api-1.0-sdk",
        unresolvedMethodReferences.size(),
        is(0));
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
        StaticLinkageChecker.findUnresolvedReferences(
            pathsForJar, Arrays.asList(arrayCloneMethod), new HashSet<>());
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
}
