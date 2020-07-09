/*
 * Copyright 2019 Google LLC.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import com.google.cloud.tools.opensource.classpath.TestHelper;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.UnresolvableArtifactProblem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class ClassPathBuilderTest {
  private ClassPathBuilder classPathBuilder = new ClassPathBuilder();

  private ImmutableList<ClassPathEntry> resolveClassPath(String coordinates) {
    Artifact artifact = new DefaultArtifact(coordinates);
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(artifact), true);
    return result.getClassPath();
  }
  
  
  @Test
  public void testResolve_withoutOptionalDependencies() {
    // an artifact with a very large dependency graph
    String coords = "org.apache.beam:beam-sdks-java-io-hcatalog:2.19.0";
    
    Artifact catalog = new DefaultArtifact(coords);
    try {
      ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(catalog), false);
      assertNotNull(result);
    } catch (OutOfMemoryError failure) {
      failure.printStackTrace();
      fail("Ran out of memory");
    } finally {
      System.gc();      
    }
  }

  @Test
  public void testResolve_removingDuplicates() {
    Artifact grpcArtifact = new DefaultArtifact("io.grpc:grpc-auth:1.15.1");
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(grpcArtifact), true);

    ImmutableList<ClassPathEntry> classPath = result.getClassPath();
    long jsr305Count =
        classPath.stream().filter(path -> path.toString().contains("jsr305")).count();
    Truth.assertWithMessage("There should not be duplicated versions of jsr305")
        .that(jsr305Count)
        .isEqualTo(1);

    Optional<ClassPathEntry> opencensusApiPathFound =
        classPath.stream().filter(path -> path.toString().contains("opencensus-api")).findFirst();
    Truth8.assertThat(opencensusApiPathFound).isPresent();
    ClassPathEntry opencensusApiPath = opencensusApiPathFound.get();
    Truth.assertWithMessage("Opencensus API should have multiple dependency paths")
        .that(result.getDependencyPaths(opencensusApiPath).size())
        .isGreaterThan(1);
  }

  /** Test that BOM members come before the transitive dependencies. */
  @Test
  public void testBomToPaths_firstElementsAreBomMembers() throws RepositoryException {
    List<Artifact> managedDependencies = 
        Bom.readBom("com.google.cloud:google-cloud-bom:0.81.0-alpha")
        .getManagedDependencies();

    ImmutableList<ClassPathEntry> classPath =
        classPathBuilder.resolve(managedDependencies, true).getClassPath();

    ImmutableList<ClassPathEntry> entries = ImmutableList.copyOf(classPath);

    Truth.assertThat(entries.get(0).toString())
        .isEqualTo("com.google.api:api-common:1.7.0"); // first element in the BOM
    int bomSize = managedDependencies.size();
    String lastFileName = entries.get(bomSize - 1).toString();
    Truth.assertThat(lastFileName)
        .isEqualTo("com.google.api:gax-httpjson:0.57.0"); // last element in BOM
  }

  @Test
  public void testResolve() {

    Artifact grpcAuth = new DefaultArtifact("io.grpc:grpc-auth:1.15.1");

    ImmutableList<ClassPathEntry> classPath =
        classPathBuilder.resolve(ImmutableList.of(grpcAuth), true).getClassPath();

    Truth.assertThat(classPath)
        .comparingElementsUsing(TestHelper.COORDINATES)
        .containsAtLeast(
            "io.grpc:grpc-auth:1.15.1", "com.google.auth:google-auth-library-credentials:0.9.0");
    classPath.forEach(
        path ->
            Truth.assertWithMessage("Every returned path should be an absolute path")
                .that(path.getJar().isAbsolute())
                .isTrue());
  }

  @Test
  public void testResolveClassPath_validCoordinate() {
    List<ClassPathEntry> entries = resolveClassPath("io.grpc:grpc-auth:1.15.1");

    Truth.assertThat(entries)
        .comparingElementsUsing(TestHelper.COORDINATES)
        .contains("io.grpc:grpc-auth:1.15.1");
    Truth.assertThat(entries)
        .comparingElementsUsing(TestHelper.COORDINATES)
        .contains("com.google.auth:google-auth-library-credentials:0.9.0");
    entries.forEach(
        entry ->
            Truth.assertWithMessage("Every returned path should be an absolute path")
                .that(entry.getJar().isAbsolute())
                .isTrue());
  }

  @Test
  public void testResolveClassPath_optionalDependency() {
    List<ClassPathEntry> classPath =
        resolveClassPath("com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha");
    Truth.assertThat(classPath).comparingElementsUsing(TestHelper.COORDINATES)
        .contains("log4j:log4j:1.2.12");
  }

  @Test
  public void testResolveClassPath_invalidCoordinate() {
    Artifact nonExistentArtifact = new DefaultArtifact("io.grpc:nosuchartifact:1.2.3");
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(nonExistentArtifact), true);
    ImmutableList<UnresolvableArtifactProblem> artifactProblems = result.getArtifactProblems();
    Truth.assertThat(artifactProblems).hasSize(1);
    assertEquals(
        "io.grpc:nosuchartifact:jar:1.2.3 was not resolved. Dependency path:"
            + " io.grpc:nosuchartifact:jar:1.2.3 (compile)",
        artifactProblems.get(0).toString());
  }

  @Test
  public void testResolve_emptyInput() {
    List<ClassPathEntry> classPath = classPathBuilder.resolve(ImmutableList.of(), true).getClassPath();
    Truth.assertThat(classPath).isEmpty();
  }

  @Test
  public void testFindInvalidReferences_selfReferenceFromAbstractClassToInterface()
      throws IOException {
    List<ClassPathEntry> classPath =
        resolveClassPath("com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha");
    ClassPathEntry httpClientJar =
        classPath.stream()
            .filter(path -> path.getJar().toString().contains("httpclient-4.5.3.jar"))
            .findFirst()
            .get();
    LinkageChecker linkageChecker = LinkageChecker.create(classPath);

    // httpclient-4.5.3 AbstractVerifier has a method reference of
    // 'void verify(String host, String[] cns, String[] subjectAlts)' to itself and its interface
    // X509HostnameVerifier has the method.
    // https://github.com/apache/httpcomponents-client/blob/e2cf733c60f910d17dc5cfc0a77797054a2e322e/httpclient/src/main/java/org/apache/http/conn/ssl/AbstractVerifier.java#L153
    ClassDumper dumper = ClassDumper.create(ImmutableList.of(httpClientJar));

    SymbolReferences symbolReferences = dumper.findSymbolReferences();

    Truth.assertWithMessage(
            "httpclient-4.5.3 shoud not contain GZipInputStreamFactory reference, which is added"
                + " 4.5.4")
        .that(symbolReferences.getClassSymbols(
            new ClassFile(
                httpClientJar, "org.apache.http.client.protocol.ResponseContentEncoding")))
        .doesNotContain(
            new ClassSymbol("org.apache.http.client.entity.GZIPInputStreamFactory"));

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.findSymbolProblems();
    assertEquals(
        "Method references within the same jar file should not be reported",
        0,
        symbolProblems.values().stream()
            .filter(classFile -> httpClientJar.equals(classFile.getClassPathEntry()))
            .count());
  }

  @Test
  public void testResolveClasspath_notToGenerateRepositoryException() throws IOException {
    List<ClassPathEntry> classPath = resolveClassPath("com.google.guava:guava-gwt:jar:20.0");
    Truth.assertThat(classPath).isNotEmpty();
  }

  @Test
  public void testResolve_artifactProblems() {
    // In the full dependency tree of hibernate-core, xerces-impl:2.6.2 and xml-apis:2.6.2 are not
    // available in Maven Central.
    Artifact hibernateCore = new DefaultArtifact("org.hibernate:hibernate-core:jar:3.5.1-Final");
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(hibernateCore), true);

    ImmutableList<UnresolvableArtifactProblem> artifactProblems = result.getArtifactProblems();

    List<String> coordinates = artifactProblems.stream()
            .map(x -> x.getArtifact())
            .map(x -> x.toString())
            .collect(Collectors.toList());

    Truth.assertThat(coordinates).containsExactly("xerces:xerces-impl:jar:2.6.2",
        "xml-apis:xml-apis:jar:2.6.2");
  }

  @Test
  public void testResolve_shouldNotRaiseStackOverflowErrorOnJUnit() {
    // There was StackOverflowError beam-sdks-java-extensions-sql-zetasql:jar:2.19.0, which was
    // caused by a cycle of the following artifacts:
    // junit:junit:jar:4.10 (compile?)
    //   org.hamcrest:hamcrest-core:jar:1.1 (compile)
    //     jmock:jmock:jar:1.1.0 (provided)
    //       junit:junit:jar:3.8.1 (compile)
    //         org.hamcrest:hamcrest-core:jar:1.1 (compile)
    Artifact beamZetaSqlExtensions = new DefaultArtifact("junit:junit:jar:4.10");

    // This should not throw StackOverflowError
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(beamZetaSqlExtensions), true);
    assertNotNull(result);
  }
}
