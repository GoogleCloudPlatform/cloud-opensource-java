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

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClassDumperTest {
  private static final String GRPC_CLOUD_FIRESTORE_JAR =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar";

  // We're sure that FirestoreGrpc class comes from this class file because
  // this project (cloud-opensource-java) doesn't have dependency for Cloud Firestore
  private static final String EXAMPLE_CLASS_FILE =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0_FirestoreGrpc.class";

  private static final Correspondence<Symbol, String> SYMBOL_TARGET_CLASS_NAME =
      Correspondence.from(
          (actual, expected) -> actual.getClassName().equals(expected), "has class name equal to");

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
  public void testCreationInvalidInput() throws IOException {
    try {
      ClassDumper.create(ImmutableList.of(Paths.get("no_such_file")));
      Assert.fail("Empty path should generate IOException");
    } catch (IllegalArgumentException ex) {
      // pass
      Truth.assertThat(ex)
          .hasMessageThat()
          .isEqualTo("Some jar files are not readable: [no_such_file]");
    }
  }

  @Test
  public void testScanSymbolTableFromClassPath() throws URISyntaxException, IOException {
    Path path = absolutePathOfResource(GRPC_CLOUD_FIRESTORE_JAR);
    ClassToSymbolReferences classToSymbolReferences =
        ClassDumper.create(ImmutableList.of(path)).scanSymbolReferencesInClassPath();

    // Class reference
    Truth.assertWithMessage("Class reference should have binary names defined in JLS 13.1")
        .that(classToSymbolReferences.getClassToClassSymbols())
        .containsEntry(
            new ClassAndJar(path, "com.google.firestore.v1beta1.FirestoreGrpc"),
            new ClassSymbol(
                "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreMethodDescriptorSupplier"));

    Truth.assertWithMessage("Reference to superclass should have SuperClassSymbol")
        .that(classToSymbolReferences.getClassToClassSymbols())
        .containsEntry(
            new ClassAndJar(path, "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreFutureStub"),
            new SuperClassSymbol("io.grpc.stub.AbstractStub"));

    // Method reference
    Truth.assertThat(classToSymbolReferences.getClassToMethodSymbols())
        .containsEntry(
            new ClassAndJar(path, "com.google.firestore.v1beta1.FirestoreGrpc"),
            new MethodSymbol(
                "io.grpc.protobuf.ProtoUtils",
                "marshaller",
                "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
                false));

    // Field reference
    Truth.assertThat(classToSymbolReferences.getClassToFieldSymbols())
        .containsEntry(
            new ClassAndJar(path, "com.google.firestore.v1beta1.FirestoreGrpc"),
            new FieldSymbol(
                "io.grpc.MethodDescriptor$MethodType",
                "BIDI_STREAMING",
                "Lio/grpc/MethodDescriptor$MethodType;"));
  }

  @Test
  public void testScanSymbolTableFromJar_shouldNotPickArrayClass()
      throws URISyntaxException, IOException {
    URL jarUrl = URLClassLoader.getSystemResource("testdata/gax-1.32.0.jar");

    Path path = Paths.get(jarUrl.toURI());
    ClassToSymbolReferences classToSymbolReferences =
        ClassDumper.create(ImmutableList.of(path)).scanSymbolReferencesInClassPath();

    Truth.assertThat(classToSymbolReferences.getClassToClassSymbols().inverse().keys())
        .comparingElementsUsing(SYMBOL_TARGET_CLASS_NAME)
        .doesNotContain("[Ljava.lang.Object;");
  }

  @Test
  public void testScanSymbolReferencesInClass_shouldPickInterfaceReference()
      throws URISyntaxException, IOException {
    Path path = absolutePathOfResource("testdata/api-common-1.7.0.jar");
    ClassToSymbolReferences classToSymbolReferences =
        ClassDumper.create(ImmutableList.of(path)).scanSymbolReferencesInClassPath();

    boolean isInterfaceMethod = true;
    Truth.assertThat(classToSymbolReferences.getClassToMethodSymbols())
        .containsEntry(
            new ClassAndJar(path, "com.google.api.resourcenames.UntypedResourceName"),
            new MethodSymbol(
                "java.util.Map",
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                isInterfaceMethod));
  }

  @Test
  public void testClassesInSamePackage() {
    Truth.assertThat(ClassDumper.classesInSamePackage("foo.Abc", "bar.Abc")).isFalse();
    Truth.assertThat(ClassDumper.classesInSamePackage("foo.bar.Abc", "foo.bar.Cde")).isTrue();
    Truth.assertThat(ClassDumper.classesInSamePackage("foo.bar.Abc$XYZ", "foo.bar.Cde")).isTrue();
    Truth.assertThat(ClassDumper.classesInSamePackage("Abc", "Cde")).isTrue();
    Truth.assertThat(ClassDumper.classesInSamePackage("Abc", "xyz.Cde")).isFalse();
  }

  @Test
  public void testEnclosingClassNames() {
    String actualName = ClassDumper.enclosingClassName("com.google.Foo$Bar$Baz");
    Truth.assertThat(actualName).isEqualTo("com.google.Foo$Bar");
    String topLevelClass = ClassDumper.enclosingClassName("com.google.Foo");
    Truth.assertThat(topLevelClass).isNull();
  }

  @Test
  public void testMapJarToClasses_classWithDollars()
      throws IOException, RepositoryException {
    Artifact grpcArtifact = new DefaultArtifact("com.google.code.gson:gson:2.6.2");
    List<Path> paths = ClassPathBuilder.artifactsToClasspath(ImmutableList.of(grpcArtifact));
    Path gsonJar = paths.get(0);

    ImmutableSetMultimap<Path, String> pathToClasses = ClassDumper
        .mapJarToClasses(paths.subList(0, 1));
    ImmutableSet<String> classesInGsonJar = pathToClasses.get(gsonJar);
    // Dollar character ($) is a valid character for a class name, not just for nested ones.
    Truth.assertThat(classesInGsonJar).contains("com.google.gson.internal.$Gson$Preconditions");
  }

  @Test
  public void testFindClassLocation() throws URISyntaxException, IOException {
    Path firestore65 = absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar");
    Path firestore66 = absolutePathOfResource("testdata/google-cloud-firestore-0.66.0-beta.jar");

    // This class exists in both jar files
    String grpcClass = "com.google.cloud.firestore.spi.v1beta1.GrpcFirestoreRpc";

    Path jarWith65First =
        ClassDumper.create(ImmutableList.of(firestore65, firestore66)).findClassLocation(grpcClass);
    Assert.assertEquals(firestore65, jarWith65First);

    Path jarWith66First =
        ClassDumper.create(ImmutableList.of(firestore66, firestore65)).findClassLocation(grpcClass);
    Assert.assertEquals(firestore66, jarWith66First);
  }

  @Test
  public void testIsSystemClass() throws URISyntaxException, IOException {
    ClassDumper classDumper =
        ClassDumper.create(ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar")));

    List<String> javaRuntimeClasses =
        ImmutableList.of(
            "java.lang.String",
            "java.lang.Object",
            "[Ljava.lang.String;",
            "java.util.ArrayList",
            "javax.net.SocketFactory"); // from rt.jar
    for (String javaRuntimeClassName : javaRuntimeClasses) {
      Truth.assertThat(classDumper.isSystemClass(javaRuntimeClassName)).isTrue();
    }

    // Even though Guava is passed to the constructor, it should not report ImmutableList as
    // Java runtime class.
    List<String> nonJavaRuntimeClasses =
        ImmutableList.of("com.google.common.collect.ImmutableList", "foo.bar.Baz");
    for (String nonJavaRuntimeClassName : nonJavaRuntimeClasses) {
      Truth.assertThat(classDumper.isSystemClass(nonJavaRuntimeClassName)).isFalse();
    }
  }

  @Test
  public void testIsUnusedClassSymbolReference_unusedClassReference()
      throws IOException, URISyntaxException {
    ClassDumper classDumper =
        ClassDumper.create(
            ImmutableList.of(absolutePathOfResource("testdata/conscrypt-openjdk-uber-1.4.2.jar")));

    ClassSymbolReference referenceToUnusedClass =
        ClassSymbolReference.builder()
            .setSourceClassName("org.conscrypt.Conscrypt")
            .setSubclass(false)
            .setTargetClassName("org.conscrypt.NativeConstants")
            .build();

    // See the issue below for the analysis of inlined fields in Conscrypt:
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/301
    boolean result = classDumper.isUnusedClassSymbolReference(referenceToUnusedClass);
    Truth.assertWithMessage(
        "As the values in NativeConstants are all inlined. "
            + "There should not be any usage in Conscrypt").that(result).isTrue();
  }

  @Test
  public void testIsUnusedClassSymbolReference_usedClassReference()
      throws IOException, URISyntaxException {
    ClassDumper classDumper =
        ClassDumper.create(
            ImmutableList.of(absolutePathOfResource("testdata/conscrypt-openjdk-uber-1.4.2.jar")));

    List<String> usedClassesInConscrypt =
        ImmutableList.of(
            "org.conscrypt.OpenSSLProvider", // Used in instanceof and new
            "org.conscrypt.Conscrypt$ProviderBuilder", // Used in new and inner class
            "java.lang.IllegalArgumentException", // Used in new
            "org.conscrypt.ServerSessionContext", // Used in instanceof
            "java.util.Properties", // Used in a new
            "org.conscrypt.NativeCrypto", // Used in a static method call
            "org.conscrypt.SSLParametersImpl", // Used in a static method call
            "java.io.IOException", // Used in a catch clause
            "java.security.KeyManagementException", // Used in throws clause
            "java.lang.Throwable" // Used in catch clause
            );

    for (String usedClass : usedClassesInConscrypt) {
      ClassSymbolReference referenceToUsedClass =
          ClassSymbolReference.builder()
              .setSourceClassName("org.conscrypt.Conscrypt")
              .setSubclass(false)
              .setTargetClassName(usedClass)
              .build();
      Truth.assertWithMessage(usedClass + " should be used in the class file")
          .that(classDumper.isUnusedClassSymbolReference(referenceToUsedClass))
          .isFalse();
    }
  }

  @Test
  public void testIsUnusedClassSymbolReference_classSymbolReferenceNotFound()
      throws IOException, URISyntaxException {
    ClassDumper classDumper =
        ClassDumper.create(
            ImmutableList.of(absolutePathOfResource("testdata/conscrypt-openjdk-uber-1.4.2.jar")));

    try {
      ClassSymbolReference referenceToUnusedClass =
          ClassSymbolReference.builder()
              .setSourceClassName("org.conscrypt.Conscrypt")
              .setSubclass(false)
              .setTargetClassName("dummy.NoSuchClass")
              .build();
      classDumper.isUnusedClassSymbolReference(referenceToUnusedClass);

      Assert.fail("It should throw VerifyException when it cannot find a class symbol reference");
    } catch (VerifyException ex) {
      // pass
      Truth.assertThat(ex)
          .hasMessageThat()
          .isEqualTo(
              "When checking a class reference from org.conscrypt.Conscrypt to dummy.NoSuchClass,"
                  + " the reference to the target class is no longer found in the source class's"
                  + " constant pool.");
    }
  }
}
