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

import static com.google.cloud.tools.opensource.classpath.LinkageCheckerTest.resolvePaths;
import static com.google.cloud.tools.opensource.classpath.TestHelper.classPathEntryOfResource;
import static org.junit.Assert.assertFalse;

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
          (actual, expected) -> actual.getClassBinaryName().equals(expected),
          "has class name equal to");

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
      ClassDumper.create(ImmutableList.of(new ClassPathEntry(Paths.get("no_such_file"))));
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
    ClassPathEntry path = classPathEntryOfResource(GRPC_CLOUD_FIRESTORE_JAR);
    SymbolReferenceMaps symbolReferenceMaps =
        ClassDumper.create(ImmutableList.of(path)).findSymbolReferences();

    // Class reference
    Truth.assertWithMessage("Class reference should have binary names defined in JLS 13.1")
        .that(symbolReferenceMaps.getClassToClassSymbols())
        .containsEntry(
            new ClassFile(path, "com.google.firestore.v1beta1.FirestoreGrpc"),
            new ClassSymbol(
                "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreMethodDescriptorSupplier"));

    Truth.assertWithMessage("Reference to superclass should have SuperClassSymbol")
        .that(symbolReferenceMaps.getClassToClassSymbols())
        .containsEntry(
            new ClassFile(path, "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreFutureStub"),
            new SuperClassSymbol("io.grpc.stub.AbstractStub"));

    // Method reference
    Truth.assertThat(symbolReferenceMaps.getClassToMethodSymbols())
        .containsEntry(
            new ClassFile(path, "com.google.firestore.v1beta1.FirestoreGrpc"),
            new MethodSymbol(
                "io.grpc.protobuf.ProtoUtils",
                "marshaller",
                "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
                false));

    // Field reference
    Truth.assertThat(symbolReferenceMaps.getClassToFieldSymbols())
        .containsEntry(
            new ClassFile(path, "com.google.firestore.v1beta1.FirestoreGrpc"),
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
    SymbolReferenceMaps symbolReferenceMaps =
        ClassDumper.create(ImmutableList.of(new ClassPathEntry(path))).findSymbolReferences();

    Truth.assertThat(symbolReferenceMaps.getClassToClassSymbols().inverse().keys())
        .comparingElementsUsing(SYMBOL_TARGET_CLASS_NAME)
        .doesNotContain("[Ljava.lang.Object;");
  }

  @Test
  public void testScanSymbolReferencesInClass_shouldPickInterfaceReference()
      throws URISyntaxException, IOException {
    ClassPathEntry entry = classPathEntryOfResource("testdata/api-common-1.7.0.jar");
    SymbolReferenceMaps symbolReferenceMaps =
        ClassDumper.create(ImmutableList.of(entry)).findSymbolReferences();

    boolean isInterfaceMethod = true;
    Truth.assertThat(symbolReferenceMaps.getClassToMethodSymbols())
        .containsEntry(
            new ClassFile(entry, "com.google.api.resourcenames.UntypedResourceName"),
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
  public void testFindClassLocation() throws URISyntaxException, IOException {
    ClassPathEntry firestore65 =
        classPathEntryOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar");
    ClassPathEntry firestore66 =
        classPathEntryOfResource("testdata/google-cloud-firestore-0.66.0-beta.jar");

    // This class exists in both jar files
    String grpcClass = "com.google.cloud.firestore.spi.v1beta1.GrpcFirestoreRpc";

    ClassPathEntry pathWith65First =
        ClassDumper.create(ImmutableList.of(firestore65, firestore66)).findClassLocation(grpcClass);
    Assert.assertEquals(firestore65, pathWith65First);

    ClassPathEntry pathWith66First =
        ClassDumper.create(ImmutableList.of(firestore66, firestore65)).findClassLocation(grpcClass);
    Assert.assertEquals(firestore66, pathWith66First);
  }

  @Test
  public void testFindClassLocation_prefixedClassName() throws URISyntaxException, IOException {
    // This JAR file contains com.google.firestore.v1beta1.FirestoreGrpc under BOOT-INF/classes.
    ClassPathEntry path = classPathEntryOfResource("testdata/dummy-boot-inf-prefix.jar");
    ClassDumper classDumper = ClassDumper.create(ImmutableList.of(path));
    classDumper.findSymbolReferences();

    ClassPathEntry classLocation =
        classDumper.findClassLocation("com.google.firestore.v1beta1.FirestoreGrpc");

    Assert.assertEquals(path, classLocation);
  }

  @Test
  public void testIsSystemClass() throws URISyntaxException, IOException {
    ClassDumper classDumper =
        ClassDumper.create(
            ImmutableList.of(classPathEntryOfResource("testdata/guava-23.5-jre.jar")));

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
            ImmutableList.of(
                classPathEntryOfResource("testdata/conscrypt-openjdk-uber-1.4.2.jar")));

    // See the issue below for the analysis of inlined fields in Conscrypt:
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/301
    boolean result = classDumper.isUnusedClassSymbolReference("org.conscrypt.Conscrypt",
        new ClassSymbol("org.conscrypt.NativeConstants"));
    Truth.assertWithMessage(
        "As the values in NativeConstants are all inlined. "
            + "There should not be any usage in Conscrypt").that(result).isTrue();
  }

  @Test
  public void testIsUnusedClassSymbolReference_usedClassReference()
      throws IOException, URISyntaxException {
    ClassDumper classDumper =
        ClassDumper.create(
            ImmutableList.of(
                classPathEntryOfResource("testdata/conscrypt-openjdk-uber-1.4.2.jar")));

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
      Truth.assertWithMessage(usedClass + " should be used in the class file")
          .that(classDumper.isUnusedClassSymbolReference("org.conscrypt.Conscrypt",
              new ClassSymbol(usedClass)))
          .isFalse();
    }
  }

  @Test
  public void testIsUnusedClassSymbolReference_classSymbolReferenceNotFound()
      throws IOException, URISyntaxException {
    ClassDumper classDumper =
        ClassDumper.create(
            ImmutableList.of(
                classPathEntryOfResource("testdata/conscrypt-openjdk-uber-1.4.2.jar")));

    try {
      classDumper.isUnusedClassSymbolReference(
          "org.conscrypt.Conscrypt", new ClassSymbol("dummy.NoSuchClass"));

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

  @Test
  public void testIsUnusedClassSymbolReference_multiReleaseJar() throws IOException {
    // org.graalvm.libgraal.LibGraal class has different implementations between Java 8 and 11 via
    // Multi-release JAR of this artifact.
    List<ClassPathEntry> classPath = resolvePaths("org.graalvm.compiler:compiler:19.0.0");

    ClassDumper classDumper = ClassDumper.create(classPath);
    classDumper.findSymbolReferences();

    // There was VerifyError when handling multi-release JAR
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/890
    classDumper.isUnusedClassSymbolReference(
        "org.graalvm.libgraal.LibGraal", new ClassSymbol("jdk.vm.ci.services.Services"));
  }

  @Test
  public void testFindSymbolReferences_overLappingClass() throws IOException {
    // Both artifacts contain com.google.inject.internal.InjectorImpl$BindingsMultimap. The one from
    // sisu-guice should not appear in symbol references because guice supersedes in the class path.
    List<ClassPathEntry> classPath =
        resolvePaths("com.google.inject:guice:3.0", "org.sonatype.sisu:sisu-guice:3.2.6");
    ClassPathEntry sisuGuicePath = classPath.get(1);

    ClassDumper classDumper = ClassDumper.create(classPath);
    SymbolReferenceMaps symbolReferences = classDumper.findSymbolReferences();
    ImmutableSetMultimap<ClassSymbol, ClassFile> classReferences =
        symbolReferences.getClassToClassSymbols().inverse();
    ImmutableSet<ClassFile> classFiles =
        classReferences.get(new ClassSymbol("com.google.inject.internal.util.$Lists"));
    Truth.assertThat(classFiles)
        .doesNotContain(
            new ClassFile(
                sisuGuicePath, "com.google.inject.internal.InjectorImpl$BindingsMultimap"));
  }

  @Test
  public void testListClasses_unexpectedNonClassFile() throws IOException {
    // com.amazonaws:amazon-kinesis-client:1.13.0 contains an unexpected lock file
    // /unison/com/e007f77498fd27177e2ea931a06dcf50/unison/tmp/amazonaws/services/kinesis/leases/impl/LeaseTaker.class
    // https://github.com/awslabs/amazon-kinesis-client/issues/654
    List<ClassPathEntry> classPath = resolvePaths("com.amazonaws:amazon-kinesis-client:1.13.0");
    ClassDumper classDumper = ClassDumper.create(classPath);
    ClassPathEntry kinesisJar = classPath.get(0);

    // This should not raise IOException
    SymbolReferenceMaps symbolReferences = classDumper.findSymbolReferences();

    Truth.assertWithMessage("Invalid files should not stop loading valid class files")
        .that(symbolReferences.getClassToClassSymbols().keySet())
        .comparingElementsUsing(
            Correspondence.transforming(
                (ClassFile classFile) -> classFile.getClassPathEntry(), "is in the JAR file"))
        .contains(kinesisJar);
  }

  @Test
  public void testCatchesLinkageError_absentOuterClass() throws IOException {
    // Curator-client has shaded com.google.common.reflect.TypeToken$Bounds but it does not contain
    // the outer class com.google.common.reflect.TypeToken.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/1092
    List<ClassPathEntry> classPath = resolvePaths("org.apache.curator:curator-client:4.2.0");

    ClassPathEntry curatorClientJar = classPath.get(0);
    ClassDumper classDumper = ClassDumper.create(ImmutableList.of(curatorClientJar));

    // The outer class (TypeToken) does not exist in the class path.
    String innerClass = "org.apache.curator.shaded.com.google.common.reflect.TypeToken$Bounds";

    // This should not raise an exception
    assertFalse(classDumper.catchesLinkageError(innerClass));
  }

  @Test
  public void testFindSymbolReferences_catchClassFormatException() throws IOException {
    List<ClassPathEntry> classPath = resolvePaths("com.ibm.icu:icu4j:2.6.1");
    ClassDumper classDumper = ClassDumper.create(classPath);

    // This should not throw ClassFormatException
    SymbolReferenceMaps symbolReferences = classDumper.findSymbolReferences();
    Truth.assertThat(symbolReferences.getClassToClassSymbols().keySet())
        .comparingElementsUsing(
            Correspondence.transforming(
                (ClassFile classFile) -> classFile.getBinaryName(), "has class name"))
        .contains("com.ibm.icu.util.DateRule");
  }

  @Test
  public void testMethodCodeNull() throws IOException {
    // catchesLinkageError should not raise an exception for MailDateFormat's constructor's null
    // code.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/1352
    List<ClassPathEntry> classPath = resolvePaths("javax:javaee-api:6.0");
    ClassDumper classDumper = ClassDumper.create(classPath);

    classDumper.catchesLinkageError("javax.mail.internet.MailDateFormat");
  }
}
