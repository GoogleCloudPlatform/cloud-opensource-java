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

import static com.google.cloud.tools.opensource.classpath.ClassPathBuilderTest.PATH_FILE_NAMES;
import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;

import com.google.cloud.tools.opensource.classpath.SymbolNotResolvable.Reason;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Test;

public class LinkageCheckerTest {

  @Test
  public void testFindInvalidReferences_arrayCloneMethod() throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // Array's clone is available in Java runtime and thus should not be reported as linkage error
    MethodSymbolReference arrayClone =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("[Lio.grpc.InternalKnownTransport;")
            .setInterfaceMethod(false)
            .setMethodName("clone")
            .setDescriptor("()Ljava/lang/Object")
            .build();

    // ImmutableList does not have clone method
    MethodSymbolReference invalidCloneOnNonArray =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.common.collect.ImmutableList")
            .setInterfaceMethod(false)
            .setMethodName("clone")
            .setDescriptor("()Ljava/lang/Object")
            .build();
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder()
            .setMethodReferences(ImmutableList.of(invalidCloneOnNonArray, arrayClone))
            .build();

    Path jarNotContainingImmutableList =
        absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar");
    JarLinkageReport jarLinkageReport =
        linkageChecker.generateLinkageReport(
            jarNotContainingImmutableList, symbolReferenceSet);

    Truth.assertThat(jarLinkageReport.getMissingMethodErrors()).hasSize(1);
    Assert.assertEquals(
        invalidCloneOnNonArray, jarLinkageReport.getMissingMethodErrors().get(0).getReference());
  }

  @Test
  public void testFindInvalidReferences_constructorInAbstractClass()
      throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName(
                "com.google.common.collect.LinkedHashMultimapGwtSerializationDependencies")
            .setInterfaceMethod(false)
            .setMethodName("<init>")
            .setDescriptor("(Ljava/util/Map;)V")
            .build();
    ImmutableList<MethodSymbolReference> methodReferences = ImmutableList.of(methodSymbolReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setMethodReferences(methodReferences).build();

    JarLinkageReport jarLinkageReport = linkageChecker.generateLinkageReport(paths.get(0),
        symbolReferenceSet);

    Truth.assertThat(jarLinkageReport.getMissingMethodErrors()).isEmpty();
  }

  @Test
  public void testCheckLinkageErrorMissingInterfaceMethodAt_interfaceAndClassSeparation()
      throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ImmutableList is an abstract class, but setting isInterfaceMethod = true
    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.common.collect.ImmutableList")
            .setInterfaceMethod(true) // This is invalid
            .setMethodName("get")
            .setDescriptor("(I)Ljava/lang/Object;")
            .build();
    // When it's verified against interfaces, it should generate an error
    Optional<SymbolNotResolvable<MethodSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingMethodAt(methodSymbolReference);

    Truth8.assertThat(errorFound).isPresent();
    Truth.assertThat(errorFound.get().getReason()).isEqualTo(Reason.INCOMPATIBLE_CLASS_CHANGE);
  }

  @Test
  public void testCheckLinkageErrorMissingMethodAt_interfaceAndClassSeparation()
      throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ClassToInstanceMap is an interface, but setting isInterfaceMethod = false
    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.common.collect.ClassToInstanceMap")
            .setInterfaceMethod(false) // This is invalid
            .setMethodName("getInstance")
            .setDescriptor("(Ljava/lang/Class;)Ljava/lang/Object;")
            .build();
    // When it's verified against classes, it should generate an error
    Optional<SymbolNotResolvable<MethodSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingMethodAt(methodSymbolReference);

    Truth8.assertThat(errorFound).isPresent();
    Truth.assertThat(errorFound.get().getReason()).isEqualTo(Reason.INCOMPATIBLE_CLASS_CHANGE);
  }

  @Test
  public void testCheckLinkageErrorMissingInterfaceMethodAt_missingInterfaceMethod()
      throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ClassToInstanceMap is an interface
    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.common.collect.ClassToInstanceMap")
            .setInterfaceMethod(true)
            .setMethodName("noSuchMethod")
            .setDescriptor("(Ljava/lang/Class;)Ljava/lang/Object;")
            .build();
    // There is no such method on ClassToInstanceMap
    Optional<SymbolNotResolvable<MethodSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingMethodAt(methodSymbolReference);

    Truth8.assertThat(errorFound).isPresent();
    Truth.assertThat(errorFound.get().getReason()).isEqualTo(Reason.SYMBOL_NOT_FOUND);
  }

  @Test
  public void testFindInvalidReferences_interfaceNotImplementedAtAbstractClass()
      throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ImmutableList is an abstract class that implements List, but does not implement get() method
    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.common.collect.ImmutableList")
            .setInterfaceMethod(false)
            .setMethodName("get")
            .setDescriptor("(I)Ljava/lang/Object;")
            .build();
    Optional<SymbolNotResolvable<MethodSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingMethodAt(methodSymbolReference);

    Truth8.assertThat(errorFound).isEmpty();
  }

  @Test
  public void testCheckLinkageErrorMissingMethodAt_privateConstructor()
      throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ImmutableList is an abstract class that implements List, but does not implement get() method
    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.common.base.Absent")
            .setInterfaceMethod(false)
            // The constructor with zero arguments is marked as private
            .setMethodName("<init>")
            .setDescriptor("()V")
            .build();

    Optional<SymbolNotResolvable<MethodSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingMethodAt(methodSymbolReference);

    Truth8.assertThat(errorFound).isPresent();
    Truth.assertThat(errorFound.get().getReason()).isEqualTo(Reason.INACCESSIBLE_CLASS);
  }

  @Test
  public void testCheckLinkageErrorMissingMethodAt_protectedConstructorFromAnonymousClass()
      throws IOException, RepositoryException {
    List<Path> paths =
        ClassPathBuilder.artifactsToClasspath(
            ImmutableList.of(new DefaultArtifact("junit:junit:4.12")));
    // junit has dependency on hamcrest-core
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ImmutableList is an abstract class that implements List, but does not implement get() method
    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName("org.junit.experimental.results.ResultMatchers$1")
            .setTargetClassName("org.hamcrest.TypeSafeMatcher")
            .setInterfaceMethod(false)
            // The constructor with zero arguments is marked as private
            .setMethodName("<init>")
            .setDescriptor("()V")
            .build();

    Optional<SymbolNotResolvable<MethodSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingMethodAt(methodSymbolReference);

    // JLS 6.6.2.2 says
    // If the access is by an anonymous class instance creation expression of the form
    // new C(...){...} or ..., then the access is permitted.
    Truth8.assertThat(errorFound).isEmpty();
  }

  @Test
  public void testCheckLinkageErrorMissingMethodAt_privateMethod()
      throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ImmutableList is an abstract class that implements List, but does not implement get() method
    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.common.base.Absent")
            .setInterfaceMethod(false)
            // This method is marked as private
            .setMethodName("readResolve")
            .setDescriptor("()Ljava/lang/Object;")
            .build();

    Optional<SymbolNotResolvable<MethodSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingMethodAt(methodSymbolReference);

    Truth8.assertThat(errorFound).isPresent();
    Truth.assertThat(errorFound.get().getReason()).isEqualTo(Reason.INACCESSIBLE_CLASS);
  }

  @Test
  public void testCheckLinkageErrorMissingMethodAt_privateStaticMethod()
      throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ImmutableList is an abstract class that implements List, but does not implement get() method
    MethodSymbolReference privateStaticReference =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.common.base.Ascii")
            .setInterfaceMethod(false)
            // This method is marked as private
            .setMethodName("getAlphaIndex")
            .setDescriptor("(C)I") // private static int getAlphaIndex(char);
            .build();

    Optional<SymbolNotResolvable<MethodSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingMethodAt(privateStaticReference);

    Truth8.assertThat(errorFound).isPresent();
    Truth.assertThat(errorFound.get().getReason()).isEqualTo(Reason.INACCESSIBLE_MEMBER);
  }

  @Test
  public void testFindInvalidReferences_validField() throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    FieldSymbolReference validFieldReference =
        FieldSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .setFieldName("SERVICE_NAME") // valid in grpc-google-cloud-firestore-v1beta1-0.28.0
            .build();
    ImmutableList<FieldSymbolReference> fieldReferences = ImmutableList.of(validFieldReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setFieldReferences(fieldReferences).build();

    JarLinkageReport jarLinkageReport =
        linkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet);

    Truth.assertThat(jarLinkageReport.getMissingFieldErrors()).isEmpty();
  }

  @Test
  public void testFindInvalidReferences_nonExistentField() throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    FieldSymbolReference invalidFieldReference =
        FieldSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .setFieldName("DUMMY_FIELD") // non-existent as of version 0.28.0
            .build();
    ImmutableList<FieldSymbolReference> fieldReferences = ImmutableList.of(invalidFieldReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setFieldReferences(fieldReferences).build();

    JarLinkageReport jarLinkageReport =
        linkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet);

    Truth.assertThat(jarLinkageReport.getMissingFieldErrors()).hasSize(1);
    Truth.assertThat(jarLinkageReport.getMissingFieldErrors().get(0).getReference().getFieldName())
        .isEqualTo("DUMMY_FIELD");
    Truth.assertWithMessage("Missing field error should carry the target class location")
        .that(jarLinkageReport.getMissingFieldErrors().get(0).getTargetClassLocation().toString())
        .endsWith("grpc-google-cloud-firestore-v1beta1-0.28.0.jar");
  }

  @Test
  public void testCheckLinkageErrorMissingClassAt_guavaClassShouldNotBeAddedAutomatically()
      throws IOException, URISyntaxException {
    // The class path does not include Guava.
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/api-common-1.7.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // Guava class should not be found in the class path
    String guavaClass =
        "com.google.common.util.concurrent.ForwardingListenableFuture$SimpleForwardingListenableFuture";
    ClassSymbolReference invalidClassReference =
        ClassSymbolReference.builder()
            // This source class file is in the firestore jar
            .setSourceClassName("com.google.api.core.ListenableFutureToApiFuture")
            .setSubclass(false)
            .setTargetClassName(guavaClass)
            .build();

    // There should be an error reported for the reference
    Optional<SymbolNotResolvable<ClassSymbolReference>> classSymbolError =
        linkageChecker.checkLinkageErrorMissingClassAt(invalidClassReference);
    Truth8.assertThat(classSymbolError).isPresent();
    Truth.assertThat(classSymbolError.get().getReference()).isEqualTo(invalidClassReference);
  }

  @Test
  public void testCheckLinkageErrorMissingClassAt_validClassInJar()
      throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // FirestoreGrpc class exists in the jar file
    ClassSymbolReference invalidClassReference =
        ClassSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setSubclass(false)
            .setTargetClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .build();

    // There should not be an error reported for the reference
    Optional<SymbolNotResolvable<ClassSymbolReference>> classSymbolError =
        linkageChecker.checkLinkageErrorMissingClassAt(invalidClassReference);
    Truth8.assertThat(classSymbolError).isEmpty();
  }

  @Test
  public void testCheckLinkageErrorMissingClassAt_invalidSuperclass()
      throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    ClassSymbolReference invalidClassReference =
        ClassSymbolReference.builder()
            .setSourceClassName("com.google.firestore.v1beta1.FirestoreGrpc") // dummy value
            .setSubclass(true) // invalid because FirestoreGrpc is a final class
            .setTargetClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .build();

    Optional<SymbolNotResolvable<ClassSymbolReference>> classSymbolError =
        linkageChecker.checkLinkageErrorMissingClassAt(invalidClassReference);
    Truth8.assertThat(classSymbolError).isPresent();
    Truth.assertThat(classSymbolError.get().getReason())
        .isEqualTo(Reason.INCOMPATIBLE_CLASS_CHANGE);
  }

  @Test
  public void testCheckLinkageErrorMissingClassAt_invalidMethodOverriding()
      throws RepositoryException, IOException {
    // cglib 2.2 does not work with asm 4. Stackoverflow post explaining VerifyError:
    // https://stackoverflow.com/questions/21059019/cglib-is-causing-a-java-lang-verifyerror-during-query-generation-in-intuit-partn
    List<Path> paths =
        ClassPathBuilder.artifactsToClasspath(
            ImmutableList.of(
                new DefaultArtifact("cglib:cglib:2.2_beta1"),
                new DefaultArtifact("org.ow2.asm:asm:4.2")));

    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    ClassSymbolReference invalidClassReference =
        ClassSymbolReference.builder()
            .setSourceClassName("net.sf.cglib.core.DebuggingClassWriter")
            .setSubclass(true)
            .setTargetClassName("org.objectweb.asm.ClassWriter")
            .build();

    Optional<SymbolNotResolvable<ClassSymbolReference>> classSymbolError =
        linkageChecker.checkLinkageErrorMissingClassAt(invalidClassReference);
    Truth8.assertThat(classSymbolError).isPresent();
    Truth.assertWithMessage(
            "ClassWriter.verify, which DebuggingClassWriter overrides, is final in asm 4")
        .that(classSymbolError.get().getReason())
        .isEqualTo(Reason.INCOMPATIBLE_CLASS_CHANGE);
  }

  @Test
  public void testCheckLinkageErrorMissingFieldAt_privateField()
      throws IOException, URISyntaxException {
    FieldSymbolReference privateFieldReference =
        FieldSymbolReference.builder()
            .setSourceClassName(LinkageCheckerTest.class.getName())
            .setTargetClassName("com.google.api.pathtemplate.PathTemplate")
            .setFieldName("SLASH_SPLITTER")
            .build();
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/api-common-1.7.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolNotResolvable<FieldSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingFieldAt(privateFieldReference);

    Truth8.assertThat(errorFound).isPresent();
    Truth.assertWithMessage("PathTemplate.SLASH_SPLITTER is private field and not accessible.")
        .that(errorFound.get().getReason())
        .isEqualTo(Reason.INACCESSIBLE_MEMBER);
  }

  @Test
  public void testCheckLinkageErrorMissingFieldAt_protectedFieldFromSamePackage()
      throws IOException, URISyntaxException {
    String targetClassName = "com.google.common.io.CharSource$CharSequenceCharSource";
    FieldSymbolReference accessFromDifferentPackage =
        FieldSymbolReference.builder()
            .setSourceClassName("foo.bar.Baz") // access from different package
            .setTargetClassName(targetClassName)
            // seq field has protected modifier
            .setFieldName("seq")
            .build();
    FieldSymbolReference accessFromSamePackage =
        FieldSymbolReference.builder()
            .setSourceClassName("com.google.common.io.Foo") // access from same package
            .setTargetClassName(targetClassName)
            // seq field has protected modifier
            .setFieldName("seq")
            .build();

    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolNotResolvable<FieldSymbolReference>> errorOnSamePackage =
        linkageChecker.checkLinkageErrorMissingFieldAt(accessFromSamePackage);
    Truth8.assertThat(errorOnSamePackage).isEmpty();

    Optional<SymbolNotResolvable<FieldSymbolReference>> errorOnDifferentPackage =
        linkageChecker.checkLinkageErrorMissingFieldAt(accessFromDifferentPackage);
    Truth8.assertThat(errorOnDifferentPackage).isPresent();

    Truth.assertWithMessage(
            "CharSequenceCharSource.seq is protected field and is not accessible from outside package")
        .that(errorOnDifferentPackage.get().getReason())
        .isEqualTo(Reason.INACCESSIBLE_CLASS);
  }

  @Test
  public void testCheckLinkageErrorMissingFieldAt_protectedFieldFromSubclass()
      throws IOException, URISyntaxException {
    FieldSymbolReference referenceFromSubclass =
        FieldSymbolReference.builder()
            // StringCharSource extends CharSequenceCharSource
            .setSourceClassName("com.google.common.io.StringCharSource")
            .setTargetClassName("com.google.common.io.CharSource$CharSequenceCharSource")
            // seq field has protected modifier
            .setFieldName("seq")
            .build();

    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-23.5-jre.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // Because StringCharSource is in the same package, the source class is not suitable
    // for this class.
    Optional<SymbolNotResolvable<FieldSymbolReference>> errorFound =
        linkageChecker.checkLinkageErrorMissingFieldAt(referenceFromSubclass);
    Truth8.assertThat(errorFound).isEmpty();
  }

  @Test
  public void testFindInvalidClassReferences_nonExistentClass()
      throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    String nonExistentClassName = "io.grpc.MethodDescriptor";
    ClassSymbolReference invalidClassReference =
        ClassSymbolReference.builder()
            .setSourceClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .setSubclass(false)
            .setTargetClassName(nonExistentClassName)
            .build();
    ImmutableList<ClassSymbolReference> classSymbolReferences =
        ImmutableList.of(invalidClassReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setClassReferences(classSymbolReferences).build();

    JarLinkageReport jarLinkageReport =
        linkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet);

    Truth.assertThat(jarLinkageReport.getMissingClassErrors()).hasSize(1);
    Truth.assertThat(
            jarLinkageReport.getMissingClassErrors().get(0).getReference().getTargetClassName())
        .isEqualTo(nonExistentClassName);
    Truth.assertThat(
        jarLinkageReport.getMissingClassErrors().get(0).getReason())
        .isEqualTo(Reason.CLASS_NOT_FOUND);
  }

  @Test
  public void testFindClassReferences_innerClass() throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    ClassSymbolReference publicClassReference =
        ClassSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setSubclass(false)
            // This inner class is defined as public in firestore-v1beta1-0.28.0.jar
            .setTargetClassName("com.google.firestore.v1beta1.FirestoreGrpc$FirestoreStub")
            .build();
    ImmutableList<ClassSymbolReference> classReferences = ImmutableList.of(publicClassReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setClassReferences(classReferences).build();

    JarLinkageReport jarLinkageReport =
        linkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet);

    Truth.assertThat(jarLinkageReport.getMissingClassErrors()).isEmpty();
  }

  @Test
  public void testFindClassReferences_privateClass() throws IOException, URISyntaxException {
    // The superclass of AbstractApiService$InnerService (Guava's ApiService) is not in the paths
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/api-common-1.7.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    ClassSymbolReference referenceToPrivateClass =
        ClassSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setSubclass(false)
            // This private inner class is defined in firestore-v1beta1-0.28.0.jar
            .setTargetClassName("com.google.api.core.AbstractApiService$InnerService")
            .build();
    ImmutableList<ClassSymbolReference> fieldReferences = ImmutableList.of(referenceToPrivateClass);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setClassReferences(fieldReferences).build();

    JarLinkageReport jarLinkageReport =
        linkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet);

    Truth.assertThat(jarLinkageReport.getMissingClassErrors()).hasSize(1);
    SymbolNotResolvable<ClassSymbolReference> classReferenceError =
        jarLinkageReport.getMissingClassErrors().get(0);
    Truth.assertThat(classReferenceError.getReason()).isEqualTo(Reason.INACCESSIBLE_CLASS);
    Truth.assertWithMessage(
            "When the superclass is unavailable, it should report the location of InnerService")
        .that(classReferenceError.getTargetClassLocation().getFileName().toString())
        .endsWith("api-common-1.7.0.jar");
  }

  @Test
  public void testGenerateInputClasspathFromLinkageCheckOption_mavenBom()
      throws RepositoryException, ParseException {
    String bomCoordinates = "com.google.cloud:cloud-oss-bom:pom:1.0.0-SNAPSHOT";

    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("-b", bomCoordinates);
    ImmutableList<Path> inputClasspath = parsedArguments.getInputClasspath();
    Truth.assertThat(inputClasspath).isNotEmpty();
    
    // These 2 files are the first 2 artifacts in the BOM
    Assert.assertEquals("guava-26.0-android.jar", inputClasspath.get(0).getFileName().toString());
    Assert.assertEquals("guava-testlib-26.0-android.jar",
        inputClasspath.get(1).getFileName().toString());
    
    // google-cloud-bom, containing google-cloud-firestore, is in the BOM with scope:import
    for (Path path : inputClasspath) {
      if (path.getFileName().toString().startsWith("google-cloud-firestore-")) {
        return;
      }
    }
    Assert.fail("Import dependency in BOM should be resolved");
  }

  @Test
  public void testGenerateInputClasspath_mavenCoordinates()
      throws RepositoryException, ParseException {
    String mavenCoordinates =
        "com.google.cloud:google-cloud-compute:jar:0.67.0-alpha,"
            + "com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha";

    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("--artifacts", mavenCoordinates);
    List<Path> inputClasspath = parsedArguments.getInputClasspath();

    Truth.assertWithMessage(
            "The first 2 items in the classpath should be the 2 artifacts in the input")
        .that(inputClasspath.subList(0, 2))
        .comparingElementsUsing(PATH_FILE_NAMES)
        .containsExactly(
            "google-cloud-compute-0.67.0-alpha.jar", "google-cloud-bigtable-0.66.0-alpha.jar")
        .inOrder();
    Truth.assertWithMessage("The dependencies of the 2 artifacts should also be included")
        .that(inputClasspath.subList(2, inputClasspath.size()))
        .isNotEmpty();
  }

  @Test
  public void testGenerateInputClasspathFromLinkageCheckOption_mavenCoordinates_missingDependency()
      throws RepositoryException, ParseException {
    // guava-gwt has missing transitive dependency:
    //   com.google.guava:guava-gwt:jar:20.0
    //     com.google.gwt:gwt-dev:jar:2.8.0 (provided)
    //       org.eclipse.jetty:apache-jsp:jar:9.2.14.v20151106 (compile)
    //         org.mortbay.jasper:apache-jsp:jar:8.0.9.M3 (compile)
    //           org.apache.tomcat:tomcat-jasper:jar:8.0.9 (compile, optional:true)
    //             org.eclipse.jdt.core.compiler:ecj:jar:4.4RC4 (not found in Maven central)
    // Because such case is possible, LinkageChecker should not abort execution when
    // the unavailable dependency is under certain condition
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("--artifacts", "com.google.guava:guava-gwt:20.0");

    ImmutableList<Path> inputClasspath = parsedArguments.getInputClasspath();

    Truth.assertThat(inputClasspath)
        .comparingElementsUsing(PATH_FILE_NAMES)
        .contains("apache-jsp-8.0.9.M3.jar");
  }

  @Test
  public void testGenerateInputClasspathFromLinkageCheckOption_failOnMissingDependency()
      throws ParseException {
    // tomcat-jasper has missing dependency (not optional):
    //   org.apache.tomcat:tomcat-jasper:jar:8.0.9
    //     org.eclipse.jdt.core.compiler:ecj:jar:4.4RC4 (not found in Maven central)
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "--artifacts", "org.apache.tomcat:tomcat-jasper:8.0.9");

    try {
      parsedArguments.getInputClasspath();
      Assert.fail(
          "Because the unavailable dependency is not optional, it should throw an exception");
    } catch (RepositoryException ex) {
      Truth.assertThat(ex.getMessage())
          .startsWith(
              "Could not find artifact org.eclipse.jdt.core.compiler:ecj:jar:4.4RC4 in central");
    }
  }
  
  @Test
  public void testGenerateInputClasspath_jarFileList()
      throws RepositoryException, ParseException {

    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("--jars", "dir1/foo.jar,dir2/bar.jar,baz.jar");
    List<Path> inputClasspath = parsedArguments.getInputClasspath();

    Truth.assertThat(inputClasspath)
        .comparingElementsUsing(PATH_FILE_NAMES)
        .containsExactly("foo.jar", "bar.jar", "baz.jar");
  }

  @Test
  public void testJarPathOrderInResolvingReferences()
      throws IOException, ClassNotFoundException, URISyntaxException {

    // listDocuments method on CollectionReference class is added at version 0.66.0-beta
    // https://github.com/googleapis/google-cloud-java/releases/tag/v0.66.0
    List<Path> firestoreDependencies =
        Lists.newArrayList(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            absolutePathOfResource("testdata/api-common-1.7.0.jar"),
            absolutePathOfResource("testdata/google-cloud-core-1.48.0.jar"),
            absolutePathOfResource("testdata/google-cloud-core-grpc-1.48.0.jar"));
    List<Path> pathsForJarWithVersion65First =
        Lists.newArrayList(
            absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar"),
            absolutePathOfResource("testdata/google-cloud-firestore-0.66.0-beta.jar"));
    pathsForJarWithVersion65First.addAll(firestoreDependencies);

    LinkageChecker linkageChecker65First =
        LinkageChecker.create(
            pathsForJarWithVersion65First,
            ImmutableSet.copyOf(pathsForJarWithVersion65First));

    List<Path> pathsForJarWithVersion66First =
        Lists.newArrayList(
            absolutePathOfResource("testdata/google-cloud-firestore-0.66.0-beta.jar"),
            absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar"));
    pathsForJarWithVersion66First.addAll(firestoreDependencies);
    LinkageChecker linkageChecker66First =
        LinkageChecker.create(
            pathsForJarWithVersion66First,
            ImmutableSet.copyOf(pathsForJarWithVersion66First));

    MethodSymbolReference listDocument =
        MethodSymbolReference.builder()
            .setSourceClassName(LinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.cloud.firestore.CollectionReference")
            .setInterfaceMethod(false)
            .setMethodName("listDocuments")
            .setDescriptor("()Ljava/lang/Iterable;")
            .build();
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setMethodReferences(ImmutableList.of(listDocument)).build();

    JarLinkageReport reportWith65First =
        linkageChecker65First.generateLinkageReport(
            firestoreDependencies.get(0), symbolReferenceSet);
    Truth.assertWithMessage("Firestore version 65 does not have CollectionReference.listDocuments")
        .that(reportWith65First.getMissingMethodErrors())
        .hasSize(1);

    JarLinkageReport reportWith66First =
        linkageChecker66First.generateLinkageReport(
            firestoreDependencies.get(0), symbolReferenceSet);
    Truth.assertWithMessage("Firestore version 66 has CollectionReference.listDocuments")
        .that(reportWith66First.getMissingMethodErrors())
        .isEmpty();
  }

  @Test
  public void testFindLinkageErrors_catchesNoClassDefFoundError()
      throws RepositoryException, IOException {
    // SLF4J classes catch NoClassDefFoundError to detect the availability of logger backends
    // the tool should not show errors for such classes.
    List<Path> paths =
        ClassPathBuilder.artifactsToClasspath(
            ImmutableList.of(new DefaultArtifact("org.slf4j:slf4j-api:jar:1.7.21")));

    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    LinkageCheckReport linkageErrors = linkageChecker.findLinkageErrors();

    JarLinkageReport slf4jLinkageReport = linkageErrors.getJarLinkageReports().get(0);
    Truth.assertThat(slf4jLinkageReport.getMissingClassErrors()).isEmpty();
    Truth.assertThat(slf4jLinkageReport.getMissingFieldErrors()).isEmpty();
    Truth.assertThat(slf4jLinkageReport.getMissingMethodErrors()).isEmpty();
  }

  @Test
  public void testFindLinkageErrors_doesNotCatchNoClassDefFoundError()
      throws URISyntaxException, IOException {
    // Checking Firestore jar file without its dependency should have linkage errors
    // Note that FirestoreGrpc.java does not have catch clause of NoClassDefFoundError
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    LinkageCheckReport linkageErrors = linkageChecker.findLinkageErrors();

    JarLinkageReport firestoreLinkageReport = linkageErrors.getJarLinkageReports().get(0);
    Truth.assertThat(firestoreLinkageReport.getMissingClassErrors()).isNotEmpty();
    Truth.assertThat(firestoreLinkageReport.getMissingMethodErrors()).isNotEmpty();
    Truth.assertThat(firestoreLinkageReport.getMissingFieldErrors()).isNotEmpty();
  }
}
