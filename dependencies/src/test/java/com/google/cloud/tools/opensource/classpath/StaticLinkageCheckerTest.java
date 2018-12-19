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

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Test;

public class StaticLinkageCheckerTest {

  private static final Correspondence<Path, String> PATH_FILE_NAMES =
      new Correspondence<Path, String>() {
        @Override
        public boolean compare(Path actual, String expected) {
          return actual.getFileName().toString().equals(expected);
        }

        @Override
        public String toString() {
          return "has file name equal to";
        }
      };

  private static Path absolutePathOfResource(String resourceName) throws URISyntaxException {
    return Paths.get(URLClassLoader.getSystemResource(resourceName).toURI()).toAbsolutePath();
  }
  
  @Test
  public void testArtifactsToPaths() throws RepositoryException {
    
    Artifact grpcArtifact = new DefaultArtifact("io.grpc:grpc-auth:1.15.1");
    ListMultimap<Path, DependencyPath> multimap =
        StaticLinkageChecker.artifactsToPaths(ImmutableList.of(grpcArtifact));

    Set<Path> paths = multimap.keySet();
    
    Truth.assertThat(paths)
        .comparingElementsUsing(PATH_FILE_NAMES)
        .containsAllOf("grpc-auth-1.15.1.jar", "google-auth-library-credentials-0.9.0.jar");
    paths.forEach(
        path ->
            Truth.assertWithMessage("Every returned path should be an absolute path")
                .that(path.isAbsolute())
                .isTrue());
  }

  @Test
  public void testCoordinateToClasspath_validCoordinate() throws RepositoryException {
    Artifact grpcArtifact = new DefaultArtifact("io.grpc:grpc-auth:1.15.1");
    List<Path> paths = StaticLinkageChecker.artifactsToClasspath(ImmutableList.of(grpcArtifact));

    Truth.assertThat(paths)
        .comparingElementsUsing(PATH_FILE_NAMES)
        .contains("grpc-auth-1.15.1.jar");
    Truth.assertThat(paths)
        .comparingElementsUsing(PATH_FILE_NAMES)
        .contains("google-auth-library-credentials-0.9.0.jar");
    paths.forEach(
        path ->
            Truth.assertWithMessage("Every returned path should be an absolute path")
                .that(path.isAbsolute())
                .isTrue());
  }

  @Test
  public void testCoordinateToClasspath_optionalDependency() throws RepositoryException {
    Artifact bigTableArtifact =
        new DefaultArtifact("com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha");
    List<Path> paths =
        StaticLinkageChecker.artifactsToClasspath(ImmutableList.of(bigTableArtifact));
    Truth.assertThat(paths).comparingElementsUsing(PATH_FILE_NAMES).contains("log4j-1.2.12.jar");
  }

  @Test
  public void testCoordinateToClasspath_invalidCoordinate() {
    Artifact nonExistentArtifact = new DefaultArtifact("io.grpc:nosuchartifact:1.2.3");
    try {
      StaticLinkageChecker.artifactsToClasspath(ImmutableList.of(nonExistentArtifact));
      Assert.fail("Invalid Maven coodinate should raise RepositoryException");
    } catch (RepositoryException ex) {
      Truth.assertThat(ex.getMessage())
          .contains("Could not find artifact io.grpc:nosuchartifact:jar:1.2.3");
    }
  }

  @Test
  public void testCoordinateToClasspath_emptyInput() throws RepositoryException {
      List<Path> jars = StaticLinkageChecker.artifactsToClasspath(ImmutableList.of());
      Truth.assertThat(jars).isEmpty();
  }

  @Test
  public void testFindInvalidReferences_selfReferenceFromAbstractClassToInterface()
      throws RepositoryException, IOException, ClassNotFoundException {
    Artifact bigTableArtifact =
        new DefaultArtifact("com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha");
    List<Path> paths =
        StaticLinkageChecker.artifactsToClasspath(ImmutableList.of(bigTableArtifact));
    Path httpClientJar =
        paths
            .stream()
            .filter(path -> "httpclient-4.5.3.jar".equals(path.getFileName().toString()))
            .findFirst()
            .get();
    StaticLinkageChecker staticLinkageChecker =
        StaticLinkageChecker.create(false, paths, ImmutableSet.copyOf(paths));

    // httpclient-4.5.3 AbstractVerifier has a method reference of
    // 'void verify(String host, String[] cns, String[] subjectAlts)' to itself and its interface
    // X509HostnameVerifier has the method.
    // https://github.com/apache/httpcomponents-client/blob/e2cf733c60f910d17dc5cfc0a77797054a2e322e/httpclient/src/main/java/org/apache/http/conn/ssl/AbstractVerifier.java#L153
    SymbolReferenceSet symbolReferenceSet = ClassDumper.scanSymbolReferencesInJar(httpClientJar);

    JarLinkageReport jarLinkageReport = staticLinkageChecker.generateLinkageReport(httpClientJar,
        symbolReferenceSet, Collections.emptyList());

    Truth.assertWithMessage("Method references within the same jar file should not be reported")
        .that(jarLinkageReport.getMissingMethodErrors())
        .isEmpty();
  }

  @Test
  public void testFindInvalidReferences_arrayCloneMethod()
      throws IOException, ClassNotFoundException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-26.0-jre.jar"));
    StaticLinkageChecker staticLinkageChecker =
        StaticLinkageChecker.create(false, paths, ImmutableSet.copyOf(paths));

    MethodSymbolReference arrayClone =
        MethodSymbolReference.builder()
            .setSourceClassName(StaticLinkageCheckReportTest.class.getName())
            .setTargetClassName("[Lio.grpc.InternalKnownTransport;")
            .setMethodName("clone")
            .setDescriptor("()Ljava/lang/Object")
            .build();
    MethodSymbolReference arrayInvalidMethod =
        MethodSymbolReference.builder()
            .setSourceClassName(StaticLinkageCheckReportTest.class.getName())
            .setTargetClassName("[Lio.grpc.InternalKnownTransport;")
            .setMethodName("foobar")
            .setDescriptor("()Ljava/lang/Object")
            .build();
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder()
            .setMethodReferences(ImmutableList.of(arrayClone, arrayInvalidMethod))
            .build();

    JarLinkageReport jarLinkageReport =
        staticLinkageChecker.generateLinkageReport(paths.get(0), symbolReferenceSet, 
            Collections.emptyList());

    Truth.assertThat(jarLinkageReport.getMissingMethodErrors()).hasSize(1);
    Assert.assertEquals(
        arrayInvalidMethod, jarLinkageReport.getMissingMethodErrors().get(0).getReference());
  }

  @Test
  public void testFindInvalidReferences_constructorInAbstractClass()
      throws IOException, ClassNotFoundException, URISyntaxException {
    List<Path> paths = ImmutableList.of(absolutePathOfResource("testdata/guava-26.0-jre.jar"));
    StaticLinkageChecker staticLinkageChecker =
        StaticLinkageChecker.create(false, paths, ImmutableSet.copyOf(paths));

    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setSourceClassName(StaticLinkageCheckReportTest.class.getName())
            .setTargetClassName(
                "com.google.common.collect.LinkedHashMultimapGwtSerializationDependencies")
            .setMethodName("<init>")
            .setDescriptor("(Ljava/util/Map;)V")
            .build();
    ImmutableList<MethodSymbolReference> methodReferences = ImmutableList.of(methodSymbolReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setMethodReferences(methodReferences).build();

    JarLinkageReport jarLinkageReport = staticLinkageChecker.generateLinkageReport(paths.get(0),
        symbolReferenceSet, Collections.emptyList());

    Truth.assertThat(jarLinkageReport.getMissingMethodErrors()).isEmpty();
  }

  @Test
  public void testFindInvalidReferences_validField() throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    StaticLinkageChecker staticLinkageChecker =
        StaticLinkageChecker.create(false, paths, ImmutableSet.copyOf(paths));

    FieldSymbolReference validFieldReference =
        FieldSymbolReference.builder()
            .setSourceClassName(StaticLinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .setFieldName("SERVICE_NAME") // valid in grpc-google-cloud-firestore-v1beta1-0.28.0
            .build();
    ImmutableList<FieldSymbolReference> fieldReferences = ImmutableList.of(validFieldReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setFieldReferences(fieldReferences).build();

    JarLinkageReport jarLinkageReport =
        staticLinkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet,
            Collections.emptyList());

    Truth.assertThat(jarLinkageReport.getMissingFieldErrors()).isEmpty();
  }

  @Test
  public void testFindInvalidReferences_nonExistentField() throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    StaticLinkageChecker staticLinkageChecker =
        StaticLinkageChecker.create(false, paths, ImmutableSet.copyOf(paths));

    FieldSymbolReference invalidFieldReference =
        FieldSymbolReference.builder()
            .setSourceClassName(StaticLinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .setFieldName("DUMMY_FIELD") // non-existent as of version 0.28.0
            .build();
    ImmutableList<FieldSymbolReference> fieldReferences = ImmutableList.of(invalidFieldReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setFieldReferences(fieldReferences).build();

    JarLinkageReport jarLinkageReport =
        staticLinkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet,
            Collections.emptyList());

    Truth.assertThat(jarLinkageReport.getMissingFieldErrors()).hasSize(1);
    Truth.assertThat(jarLinkageReport.getMissingFieldErrors().get(0).getReference().getFieldName())
        .isEqualTo("DUMMY_FIELD");
    Truth.assertWithMessage("Missing field error should carry the target class location")
        .that(jarLinkageReport.getMissingFieldErrors().get(0).getTargetClassLocation().getFile())
        .endsWith("grpc-google-cloud-firestore-v1beta1-0.28.0.jar");
  }

  @Test
  public void testFindInvalidClassReferences_nonExistentClass() throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    StaticLinkageChecker staticLinkageChecker =
        StaticLinkageChecker.create(false, paths, ImmutableSet.copyOf(paths));

    String nonExistentClassName = "com.google.firestore.v1beta1.FooBar";
    ClassSymbolReference invalidClassReference =
        ClassSymbolReference.builder()
            .setSourceClassName(StaticLinkageCheckReportTest.class.getName())
            .setTargetClassName(nonExistentClassName)
            .build();
    ImmutableList<ClassSymbolReference> fieldReferences = ImmutableList.of(invalidClassReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setClassReferences(fieldReferences).build();

    JarLinkageReport jarLinkageReport =
        staticLinkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet,
            Collections.emptyList());

    Truth.assertThat(jarLinkageReport.getMissingClassErrors()).hasSize(1);
    Truth.assertThat(
            jarLinkageReport.getMissingClassErrors().get(0).getReference().getTargetClassName())
        .isEqualTo(nonExistentClassName);
  }

  @Test
  public void testFindClassReferences_innerClass() throws IOException, URISyntaxException {
    List<Path> paths =
        ImmutableList.of(
            absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar"));
    StaticLinkageChecker staticLinkageChecker =
        StaticLinkageChecker.create(false, paths, ImmutableSet.copyOf(paths));

    ClassSymbolReference invalidClassReference =
        ClassSymbolReference.builder()
            .setSourceClassName(StaticLinkageCheckReportTest.class.getName())
            // This inner class is defined in firestore-v1beta1-0.28.0.jar
            .setTargetClassName(
                "com.google.firestore.v1beta1.FirestoreGrpc$FirestoreMethodDescriptorSupplier")
            .build();
    ImmutableList<ClassSymbolReference> fieldReferences = ImmutableList.of(invalidClassReference);
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setClassReferences(fieldReferences).build();

    JarLinkageReport jarLinkageReport =
        staticLinkageChecker.generateLinkageReport(
            absolutePathOfResource("testdata/gax-1.32.0.jar"),
            symbolReferenceSet,
            Collections.emptyList());

    Truth.assertThat(jarLinkageReport.getMissingClassErrors()).isEmpty();
  }

  @Test
  public void testGenerateInputClasspathFromLinkageCheckOption_mavenBom()
      throws RepositoryException, ParseException {
    String bomCoordinates = "com.google.cloud:cloud-oss-bom:pom:1.0.0-SNAPSHOT";

    CommandLine parsedOption =
        StaticLinkageCheckOption.readCommandLine(new String[] {"-b", bomCoordinates});
    ImmutableList<Path> inputClasspath =
        StaticLinkageCheckOption.generateInputClasspath(parsedOption);
    Truth.assertThat(inputClasspath).isNotEmpty();
    // These 3 files are the first 3 artifacts in the BOM
    Truth.assertWithMessage("The files should match the elements in the BOM")
        .that(inputClasspath.subList(0, 3))
        .comparingElementsUsing(PATH_FILE_NAMES)
        .containsExactly("guava-20.0.jar", "guava-gwt-20.0.jar", "guava-testlib-20.0.jar");

    // google-cloud-bom, containing google-cloud-firestore, is in the BOM with scope:import
    Truth.assertWithMessage("Import dependency in BOM should be resolved")
        .that(inputClasspath)
        .comparingElementsUsing(PATH_FILE_NAMES)
        .contains("google-cloud-firestore-0.70.0-beta.jar");
  }

  @Test
  public void testGenerateInputClasspath_mavenCoordinates()
      throws RepositoryException, ParseException {
    String mavenCoordinates =
        "com.google.cloud:google-cloud-compute:jar:0.67.0-alpha,"
            + "com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha";
    String[] arguments = {"--artifacts", mavenCoordinates};

    CommandLine parsedOption = StaticLinkageCheckOption.readCommandLine(arguments);
    List<Path> inputClasspath = StaticLinkageCheckOption.generateInputClasspath(parsedOption);

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
    // Because such case is possible, StaticLinkageChecker should not abort execution when
    // the unavailable dependency is under certain condition
    CommandLine parsedOption =
        StaticLinkageCheckOption.readCommandLine(
            new String[] {"--artifacts", "com.google.guava:guava-gwt:20.0"});

    ImmutableList<Path> inputClasspath =
        StaticLinkageCheckOption.generateInputClasspath(parsedOption);

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
    CommandLine parsedOption =
        StaticLinkageCheckOption.readCommandLine(
            new String[] {"--artifacts", "org.apache.tomcat:tomcat-jasper:8.0.9"});

    try {
      StaticLinkageCheckOption.generateInputClasspath(parsedOption);
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

    String[] arguments = {"--jars", "dir1/foo.jar,dir2/bar.jar,baz.jar"};
    CommandLine parsedOption = StaticLinkageCheckOption.readCommandLine(arguments);
    List<Path> inputClasspath = StaticLinkageCheckOption.generateInputClasspath(parsedOption);

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

    StaticLinkageChecker staticLinkageChecker65First =
        StaticLinkageChecker.create(
            true,
            pathsForJarWithVersion65First,
            ImmutableSet.copyOf(pathsForJarWithVersion65First));

    List<Path> pathsForJarWithVersion66First =
        Lists.newArrayList(
            absolutePathOfResource("testdata/google-cloud-firestore-0.66.0-beta.jar"),
            absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar"));
    pathsForJarWithVersion66First.addAll(firestoreDependencies);
    StaticLinkageChecker staticLinkageChecker66First =
        StaticLinkageChecker.create(
            true,
            pathsForJarWithVersion66First,
            ImmutableSet.copyOf(pathsForJarWithVersion66First));

    MethodSymbolReference listDocument =
        MethodSymbolReference.builder()
            .setSourceClassName(StaticLinkageCheckReportTest.class.getName())
            .setTargetClassName("com.google.cloud.firestore.CollectionReference")
            .setMethodName("listDocuments")
            .setDescriptor("()Ljava/lang/Iterable;")
            .build();
    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder().setMethodReferences(ImmutableList.of(listDocument)).build();

    JarLinkageReport reportWith65First =
        staticLinkageChecker65First.generateLinkageReport(
            firestoreDependencies.get(0), symbolReferenceSet, Collections.emptyList());
    Truth.assertWithMessage("Firestore version 65 does not have CollectionReference.listDocuments")
        .that(reportWith65First.getMissingMethodErrors())
        .hasSize(1);

    JarLinkageReport reportWith66First =
        staticLinkageChecker66First.generateLinkageReport(
            firestoreDependencies.get(0), symbolReferenceSet, Collections.emptyList());
    Truth.assertWithMessage("Firestore version 66 has CollectionReference.listDocuments")
        .that(reportWith66First.getMissingMethodErrors())
        .isEmpty();
  }
}
