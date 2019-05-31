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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LinkageCheckerTest {

  private Path guavaPath;
  private Path firestorePath;

  @Before
  public void setup() throws URISyntaxException {
    guavaPath = absolutePathOfResource("testdata/guava-23.5-jre.jar");
    firestorePath =
        absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar");
  }

  @Test
  public void testScannedSymbols() throws IOException {
    Path guavaAbsolutePath = guavaPath;
    List<Path> paths = ImmutableList.of(guavaAbsolutePath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    SymbolReferenceMaps classToSymbols = linkageChecker.getClassToSymbols();
    // These example symbols below are picked up through javap command. For example
    // javap -classpath src/test/resources/testdata/guava-23.5-jre.jar \
    //   -v com/google/common/util/concurrent/Monitor
    Truth.assertThat(classToSymbols.getClassToClassSymbols())
        .containsEntry(
            new ClassFile(guavaAbsolutePath, "com.google.common.util.concurrent.Service"),
            new ClassSymbol("java.util.concurrent.TimeoutException"));
    Truth.assertThat(classToSymbols.getClassToMethodSymbols())
        .containsEntry(
            new ClassFile(guavaAbsolutePath, "com.google.common.util.concurrent.Monitor"),
            new MethodSymbol(
                "com.google.common.base.Preconditions",
                "checkNotNull",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false));
    Truth.assertThat(classToSymbols.getClassToFieldSymbols())
        .containsEntry(
            new ClassFile(guavaAbsolutePath, "com.google.common.util.concurrent.Monitor"),
            new FieldSymbol("com.google.common.util.concurrent.Monitor$Guard", "waiterCount", "I"));
  }

  @Test
  public void testFindInvalidReferences_arrayCloneMethod() throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // Array's clone is available in Java runtime and thus should not be reported as linkage error
    long arraySymbolProblemCount =
        linkageChecker.findSymbolProblems().keys().stream()
            .filter(problem -> problem.getSymbol().getClassName().startsWith("["))
            .count();
    assertEquals(0, arraySymbolProblemCount);
  }

  @Test
  public void testFindInvalidReferences_constructorInAbstractClass() throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();
    builder.addMethodReference(
        new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
        new MethodSymbol(
            "com.google.common.collect.LinkedHashMultimapGwtSerializationDependencies",
            "<init>",
            "(Ljava/util/Map;)V",
            false));

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.cloneWith(builder.build()).findSymbolProblems();

    Truth.assertThat(symbolProblems).isEmpty();
  }

  @Test
  public void testCheckLinkageErrorMissingInterfaceMethodAt_interfaceAndClassSeparation()
      throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ImmutableList is an abstract class, but setting isInterfaceMethod = true to get an error
    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();
    MethodSymbol methodSymbol =
        new MethodSymbol(
            "com.google.common.collect.ImmutableList",
            "get",
            "(I)Ljava/lang/Object;",
            true); // invalid

    // When it's verified against interfaces, it should generate an error
    Optional<SymbolProblem> symbolProblem =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()), methodSymbol);

    Truth8.assertThat(symbolProblem).isPresent();
    assertSame(ErrorType.INCOMPATIBLE_CLASS_CHANGE, symbolProblem.get().getErrorType());
  }

  @Test
  public void testCheckLinkageErrorMissingMethodAt_interfaceAndClassSeparation()
      throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ClassToInstanceMap is an interface, but setting isInterfaceMethod = false
    // When it's verified against classes, it should generate an error
    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new MethodSymbol(
                "com.google.common.collect.ClassToInstanceMap",
                "getInstance",
                "(Ljava/lang/Class;)Ljava/lang/Object;",
                false));

    Truth8.assertThat(problemFound).isPresent();
    assertSame(ErrorType.INCOMPATIBLE_CLASS_CHANGE, problemFound.get().getErrorType());
  }

  @Test
  public void testCheckLinkageErrorMissingInterfaceMethodAt_missingInterfaceMethod()
      throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // There is no such method on ClassToInstanceMap
    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new MethodSymbol(
                "com.google.common.collect.ClassToInstanceMap",
                "noSuchMethod",
                "(Ljava/lang/Class;)Ljava/lang/Object;",
                true));
    Truth8.assertThat(problemFound).isPresent();
    assertSame(ErrorType.SYMBOL_NOT_FOUND, problemFound.get().getErrorType());
  }

  @Test
  public void testFindInvalidReferences_interfaceNotImplementedAtAbstractClass()
      throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // ImmutableList is an abstract class that implements List, but does not implement get() method
    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new MethodSymbol(
                "com.google.common.collect.ImmutableList", "get", "(I)Ljava/lang/Object;", false));
    Truth8.assertThat(problemFound).isEmpty();
  }

  @Test
  public void testFindSymbolProblem_privateConstructor() throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // The constructor of Absent class with zero arguments is marked as private
    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new MethodSymbol("com.google.common.base.Absent", "<init>", "()V", false));

    Truth8.assertThat(problemFound).isPresent();
    assertSame(ErrorType.INACCESSIBLE_CLASS, problemFound.get().getErrorType());
  }

  @Test
  public void testFindSymbolProblem_protectedConstructorFromAnonymousClass()
      throws IOException, RepositoryException {
    List<Path> paths =
        ClassPathBuilder.artifactsToClasspath(
            ImmutableList.of(new DefaultArtifact("junit:junit:4.12")));
    // junit has dependency on hamcrest-core
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // The constructor is protected but should be accessible from the subclasses
    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "org.junit.experimental.results.ResultMatchers$1"),
            new MethodSymbol("org.hamcrest.TypeSafeMatcher", "<init>", "()V", false));

    // JLS 6.6.2.2 says
    // If the access is by an anonymous class instance creation expression of the form
    // new C(...){...} or ..., then the access is permitted.
    Truth8.assertThat(problemFound).isEmpty();
  }

  @Test
  public void testFindSymbolProblem_inaccessibleClass() throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // Absent class is package private
    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new MethodSymbol(
                "com.google.common.base.Absent", "readResolve", "()Ljava/lang/Object;", false));
    Truth8.assertThat(problemFound).isPresent();
    assertSame(ErrorType.INACCESSIBLE_CLASS, problemFound.get().getErrorType());
  }

  @Test
  public void testFindSymbolProblem_privateStaticMethod() throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new MethodSymbol(
                "com.google.common.base.Ascii",
                // This method is marked as private
                "getAlphaIndex",
                "(C)I",
                false));
    Truth8.assertThat(problemFound).isPresent();
    assertSame(ErrorType.INACCESSIBLE_MEMBER, problemFound.get().getErrorType());
  }

  @Test
  public void testFindSymbolProblem_methodSymbolMissingClass() throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "com.google.common.collect.ImmutableList"),
            new MethodSymbol(
                "com.google.NoSuchClass", "readResolve", "()Ljava/lang/Object;", false));

    Truth8.assertThat(problemFound).isPresent();
    SymbolProblem symbolProblem = problemFound.get();
    assertSame(ErrorType.CLASS_NOT_FOUND, symbolProblem.getErrorType());
    assertTrue(
        "Method reference missing class should report it as class symbol problem",
        symbolProblem.getSymbol() instanceof ClassSymbol);
  }

  @Test
  public void testFindSymbolProblem_validField() throws IOException {
    List<Path> paths = ImmutableList.of(firestorePath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new FieldSymbol(
                "com.google.firestore.v1beta1.FirestoreGrpc",
                "SERVICE_NAME",
                "Ljava.lang.String;"));

    Truth8.assertThat(problemFound).isEmpty();
  }

  @Test
  public void testFindSymbolProblem_nonExistentField() throws IOException {
    Path firestoreJar = firestorePath;
    List<Path> paths = ImmutableList.of(firestoreJar);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(firestoreJar, LinkageCheckReportTest.class.getName()),
            new FieldSymbol(
                "com.google.firestore.v1beta1.FirestoreGrpc",
                "DUMMY_FIELD", // No such field
                "Ljava.lang.String;"));

    Truth8.assertThat(problemFound).isPresent();
    SymbolProblem symbolProblem = problemFound.get();
    assertSame(ErrorType.SYMBOL_NOT_FOUND, symbolProblem.getErrorType());
    assertEquals(firestoreJar, symbolProblem.getContainingClass().getJar());
  }

  @Test
  public void testFindSymbolProblem_fieldSymbolMissingClass() throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "com.google.common.collect.ImmutableList"),
            new FieldSymbol("com.google.NoSuchClass", "DUMMY_FIELD", "Ljava.lang.String;"));

    Truth8.assertThat(problemFound).isPresent();
    SymbolProblem symbolProblem = problemFound.get();
    assertSame(ErrorType.CLASS_NOT_FOUND, symbolProblem.getErrorType());
    assertTrue(
        "Field reference missing class should report it as class symbol problem",
        symbolProblem.getSymbol() instanceof ClassSymbol);
  }

  @Test
  public void testFindSymbolProblem_guavaClassShouldNotBeAddedAutomatically()
      throws IOException, URISyntaxException {
    // The class path does not include Guava.
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/api-common-1.7.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // Guava class should not be found in the class path
    String guavaClass =
        "com.google.common.util.concurrent.ForwardingListenableFuture$SimpleForwardingListenableFuture";

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "com.google.api.core.ListenableFutureToApiFuture"),
            new ClassSymbol(guavaClass));

    // There should be an error reported for the reference
    Truth8.assertThat(problemFound).isPresent();
    assertEquals(guavaClass, problemFound.get().getSymbol().getClassName());
  }

  @Test
  public void testFindSymbolProblem_validClassInJar() throws IOException {
    List<Path> paths = ImmutableList.of(firestorePath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // FirestoreGrpc class exists in the jar file
    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new ClassSymbol("com.google.firestore.v1beta1.FirestoreGrpc"));

    // There should not be an error reported for the reference
    Truth8.assertThat(problemFound).isEmpty();
  }

  @Test
  public void testFindSymbolProblem_invalidSuperclass() throws IOException {
    List<Path> paths = ImmutableList.of(firestorePath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "com.google.firestore.v1beta1.FirestoreGrpc"), // dummy
            // invalid because FirestoreGrpc is a final class
            new SuperClassSymbol("com.google.firestore.v1beta1.FirestoreGrpc"));

    Truth8.assertThat(problemFound).isPresent();
    assertSame(ErrorType.INCOMPATIBLE_CLASS_CHANGE, problemFound.get().getErrorType());
  }

  @Test
  public void testFindSymbolProblem_invalidMethodOverriding()
      throws RepositoryException, IOException {
    // cglib 2.2 does not work with asm 4. Stackoverflow post explaining VerifyError:
    // https://stackoverflow.com/questions/21059019/cglib-is-causing-a-java-lang-verifyerror-during-query-generation-in-intuit-partn
    List<Path> paths =
        ClassPathBuilder.artifactsToClasspath(
            ImmutableList.of(
                new DefaultArtifact("cglib:cglib:2.2_beta1"),
                new DefaultArtifact("org.ow2.asm:asm:4.2")));

    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "net.sf.cglib.core.DebuggingClassWriter"),
            // invalid because FirestoreGrpc is a final class
            new SuperClassSymbol("org.objectweb.asm.ClassWriter"));

    Truth8.assertThat(problemFound).isPresent();
    assertSame(
        "ClassWriter.verify, which DebuggingClassWriter overrides, is final in asm 4",
        ErrorType.INCOMPATIBLE_CLASS_CHANGE,
        problemFound.get().getErrorType());
  }

  @Test
  public void testFindSymbolProblem_privateField() throws IOException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/api-common-1.7.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
            new FieldSymbol(
                "com.google.api.pathtemplate.PathTemplate",
                "SLASH_SPLITTER", // private field
                "Ljava.lang.String;"));

    Truth8.assertThat(problemFound).isPresent();
    assertSame(
        "PathTemplate.SLASH_SPLITTER is private field and not accessible.",
        ErrorType.INACCESSIBLE_MEMBER,
        problemFound.get().getErrorType());
  }

  @Test
  public void testFindSymbolProblem_protectedFieldFromSamePackage() throws IOException {
    String targetClassName = "com.google.common.io.CharSource$CharSequenceCharSource";

    List<Path> paths = ImmutableList.of(guavaPath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    Optional<SymbolProblem> problemFoundSamePackage =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "com.google.common.io.Foo"), // access from same package
            new FieldSymbol(
                targetClassName,
                "seq", // private field
                "Ljava.lang.String;"));
    Truth8.assertThat(problemFoundSamePackage).isEmpty();

    Optional<SymbolProblem> problemFoundDifferentPackage =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "foo.bar.Baz"), // access from same package
            new FieldSymbol(
                targetClassName,
                "seq", // private field
                "Ljava.lang.String;"));
    Truth8.assertThat(problemFoundDifferentPackage).isPresent();

    assertSame(
        "CharSequenceCharSource.seq is protected field and is not accessible from outside package",
        ErrorType.INACCESSIBLE_CLASS,
        problemFoundDifferentPackage.get().getErrorType());
  }

  @Test
  public void testFindSymbolProblem_protectedFieldFromSubclass() throws IOException {
    List<Path> paths = ImmutableList.of(guavaPath);

    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);
    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(
                paths.get(0), "com.google.common.io.StringCharSource"), // access from same package
            new FieldSymbol(
                "com.google.common.io.CharSource$CharSequenceCharSource",
                "seq", // private field
                "Lcom.google.common.io.CharSequence;"));
    Truth8.assertThat(problemFound).isEmpty();
  }

  @Test
  public void testFindInvalidClassReferences_nonExistentClass() throws IOException {
    List<Path> paths = ImmutableList.of(firestorePath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    String nonExistentClassName = "io.grpc.MethodDescriptor";

    Optional<SymbolProblem> problemFound =
        linkageChecker.findSymbolProblem(
            new ClassFile(paths.get(0), "com.google.firestore.v1beta1.FirestoreGrpc"),
            new ClassSymbol(nonExistentClassName));
    assertSame(ErrorType.CLASS_NOT_FOUND, problemFound.get().getErrorType());
    assertEquals(nonExistentClassName, problemFound.get().getSymbol().getClassName());
  }

  @Test
  public void testFindClassReferences_innerClass() throws IOException {
    List<Path> paths = ImmutableList.of(firestorePath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();
    builder.addClassReference(
        new ClassFile(paths.get(0), LinkageCheckReportTest.class.getName()),
        // This inner class is defined as public in firestore-v1beta1-0.28.0.jar
        new ClassSymbol("com.google.firestore.v1beta1.FirestoreGrpc$FirestoreStub"));
    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.cloneWith(builder.build()).findSymbolProblems();

    Truth.assertThat(symbolProblems).isEmpty();
  }


  @Test
  public void testFindClassReferences_privateClass() throws IOException, URISyntaxException {
    // The superclass of AbstractApiService$InnerService (Guava's ApiService) is not in the paths
    Path dummySource = firestorePath;
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/api-common-1.7.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();
    builder.addClassReference(
        new ClassFile(dummySource, LinkageCheckReportTest.class.getName()),
        // This private inner class is defined in firestore-v1beta1-0.28.0.jar
        new ClassSymbol("com.google.api.core.AbstractApiService$InnerService"));
    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.cloneWith(builder.build()).findSymbolProblems();

    Truth.assertThat(symbolProblems).hasSize(1);
    Map.Entry<SymbolProblem, ClassFile> entry = symbolProblems.entries().asList().get(0);
    SymbolProblem problem = entry.getKey();
    assertSame(ErrorType.INACCESSIBLE_CLASS, problem.getErrorType());

    Truth.assertWithMessage(
            "When the superclass is unavailable, it should report the location of InnerService")
        .that(entry.getKey().getContainingClass().getJar().getFileName().toString())
        .endsWith("api-common-1.7.0.jar");
  }

  @Test
  public void testFindSymbolProblems_shouldStripSourceInnerClasses()
      throws IOException, URISyntaxException {
    // The superclass of AbstractApiService$InnerService (Guava's ApiService) is not in the paths
    Path dummySource = firestorePath;
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/api-common-1.7.0.jar"));
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();
    builder.addClassReference(
        new ClassFile(dummySource, "com.google.foo.Bar$Baz"),
        // This private inner class is defined in firestore-v1beta1-0.28.0.jar
        new ClassSymbol("com.google.api.core.AbstractApiService$InnerService"));
    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.cloneWith(builder.build()).findSymbolProblems();

    long innerClassCount =
        symbolProblems.values().stream()
            .map(ClassFile::getClassName)
            .filter(className -> className.contains("$"))
            .count();
    assertEquals(0L, innerClassCount);
  }

  @Test
  public void testGenerateInputClasspathFromLinkageCheckOption_mavenBom()
      throws RepositoryException, ParseException {
    String bomCoordinates = "com.google.cloud:google-cloud-bom:0.81.0-alpha";

    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("-b", bomCoordinates);
    ImmutableList<Path> inputClasspath = parsedArguments.getInputClasspath();
    Truth.assertThat(inputClasspath).isNotEmpty();

    List<String> names =
        inputClasspath.stream().map(x -> x.getFileName().toString()).collect(Collectors.toList());
    // The first artifacts
    Truth.assertThat(names).containsAtLeast(
        "api-common-1.7.0.jar",
        "proto-google-common-protos-1.14.0.jar",
        "grpc-google-common-protos-1.14.0.jar");

    // gax-bom, containing com.google.api:gax:1.42.0, is in the BOM with scope:import
    for (Path path : inputClasspath) {
      if (path.getFileName().toString().equals("gax-1.40.0.jar")) {
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
  public void testJarPathOrderInResolvingReferences() throws IOException, URISyntaxException {

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

    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();

    ClassFile source = new ClassFile(firestorePath, LinkageCheckReportTest.class.getName());

    builder.addMethodReference(
        source,
        // This private inner class is defined in firestore-v1beta1-0.28.0.jar
        new MethodSymbol(
            "com.google.cloud.firestore.CollectionReference",
            "listDocuments",
            "()Ljava/lang/Iterable;",
            false));
    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems65First =
        linkageChecker65First.cloneWith(builder.build()).findSymbolProblems();
    Truth.assertThat(symbolProblems65First).hasSize(1);

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems66First =
        linkageChecker66First.cloneWith(builder.build()).findSymbolProblems();
    Truth.assertThat(symbolProblems66First).isEmpty();
  }

  @Test
  public void testFindSymbolProblems_catchesNoClassDefFoundError()
      throws RepositoryException, IOException {
    // SLF4J classes catch NoClassDefFoundError to detect the availability of logger backends
    // the tool should not show errors for such classes.
    List<Path> paths =
        ClassPathBuilder.artifactsToClasspath(
            ImmutableList.of(new DefaultArtifact("org.slf4j:slf4j-api:jar:1.7.21")));

    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.findSymbolProblems();

    Truth.assertThat(symbolProblems).isEmpty();
  }

  @Test
  public void testFindSymbolProblems_doesNotCatchNoClassDefFoundError() throws IOException {
    // Checking Firestore jar file without its dependency should have linkage errors
    // Note that FirestoreGrpc.java does not have catch clause of NoClassDefFoundError
    List<Path> paths = ImmutableList.of(firestorePath);
    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.findSymbolProblems();

    boolean hasClassSymbolProblem =
        symbolProblems.keySet().stream()
            .anyMatch(problem -> problem.getSymbol() instanceof ClassSymbol);
    assertTrue(hasClassSymbolProblem);
    boolean hasMethodSymbolProblem =
        symbolProblems.keySet().stream()
            .anyMatch(problem -> problem.getSymbol() instanceof MethodSymbol);
    assertFalse(
        "Method reference missing class should be reported as class symbol problem",
        hasMethodSymbolProblem);
    boolean hasFieldSymbolProblem =
        symbolProblems.keySet().stream()
            .anyMatch(problem -> problem.getSymbol() instanceof FieldSymbol);
    assertFalse(
        "Field reference missing class should be reported as class symbol problem",
        hasFieldSymbolProblem);
  }

  @Test
  public void testFindSymbolProblems_shouldNotFailOnDuplicateClass()
      throws RepositoryException, IOException {
    // There was an issue (#495) where com.google.api.client.http.apache.ApacheHttpRequest is in
    // both google-http-client-1.19.0.jar and google-http-client-apache-2.0.0.jar.
    // LinkageChecker.findLinkageErrors was not handling the case properly.
    // These two jar files are transitive dependencies of the artifacts below.
    List<Path> paths =
        ClassPathBuilder.artifactsToClasspath(
            ImmutableList.of(
                new DefaultArtifact("io.grpc:grpc-alts:jar:1.18.0"),
                new DefaultArtifact("com.google.cloud:google-cloud-nio:jar:0.81.0-alpha")));

    LinkageChecker linkageChecker = LinkageChecker.create(paths, paths);

    // This should not raise an exception
    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.findSymbolProblems();
    assertNotNull(symbolProblems);
  }

}
