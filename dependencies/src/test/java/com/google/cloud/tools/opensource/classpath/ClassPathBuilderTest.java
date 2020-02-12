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

import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.cloud.tools.opensource.dependencies.UnresolvableArtifactProblem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class ClassPathBuilderTest {

  static final Correspondence<Path, String> PATH_FILE_NAMES =
      Correspondence.from((actual, expected) ->
          actual.getFileName().toString().equals(expected), "has file name equal to");

  private ClassPathBuilder classPathBuilder = new ClassPathBuilder();

  private ImmutableList<Path> resolveClassPath(String coordinates) throws RepositoryException {
    Artifact artifact = new DefaultArtifact(coordinates);
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(artifact));
    return result.getClassPath();
  }

  @Test
  public void testResolve_removingDuplicates() throws RepositoryException {
    Artifact grpcArtifact = new DefaultArtifact("io.grpc:grpc-auth:1.15.1");
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(grpcArtifact));

    ImmutableList<Path> paths = result.getClassPath();
    long jsr305Count = paths.stream().filter(path -> path.toString().contains("jsr305-")).count();
    Truth.assertWithMessage("There should not be duplicated versions for jsr305")
        .that(jsr305Count)
        .isEqualTo(1);

    Optional<Path> opencensusApiPathFound =
        paths.stream().filter(path -> path.toString().contains("opencensus-api-")).findFirst();
    Truth8.assertThat(opencensusApiPathFound).isPresent();
    Path opencensusApiPath = opencensusApiPathFound.get();
    Truth.assertWithMessage("Opencensus API should have multiple dependency paths")
        .that(result.getDependencyPaths(opencensusApiPath).size())
        .isGreaterThan(1);
  }

  /**
   * Test that BOM members come before the transitive dependencies.
   */
  @Test
  public void testBomToPaths_firstElementsAreBomMembers() throws RepositoryException {    
    List<Artifact> managedDependencies = 
        RepositoryUtility.readBom("com.google.cloud:google-cloud-bom:0.81.0-alpha")
        .getManagedDependencies();

    ImmutableList<Path> classPath = classPathBuilder.resolve(managedDependencies).getClassPath();

    ImmutableList<Path> paths = ImmutableList.copyOf(classPath);

    Truth.assertThat(paths.get(0).getFileName().toString()).isEqualTo(
        "api-common-1.7.0.jar"); // first element in the BOM
    int bomSize = managedDependencies.size();
    String lastFileName = paths.get(bomSize - 1).getFileName().toString();
    Truth.assertThat(lastFileName).isEqualTo("gax-httpjson-0.57.0.jar"); // last element in BOM
  }

  @Test
  public void testResolve() throws RepositoryException {

    Artifact grpcAuth = new DefaultArtifact("io.grpc:grpc-auth:1.15.1");

    ImmutableList<Path> paths = classPathBuilder.resolve(ImmutableList.of(grpcAuth)).getClassPath();

    Truth.assertThat(paths)
        .comparingElementsUsing(PATH_FILE_NAMES)
        .containsAtLeast("grpc-auth-1.15.1.jar", "google-auth-library-credentials-0.9.0.jar");
    paths.forEach(
        path ->
            Truth.assertWithMessage("Every returned path should be an absolute path")
                .that(path.isAbsolute())
                .isTrue());
  }

  @Test
  public void testresolveClassPath_validCoordinate() throws RepositoryException {
    List<Path> paths = resolveClassPath("io.grpc:grpc-auth:1.15.1");

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
  public void testResolveClassPath_optionalDependency() throws RepositoryException {
    List<Path> paths = resolveClassPath("com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha");
    Truth.assertThat(paths).comparingElementsUsing(PATH_FILE_NAMES).contains("log4j-1.2.12.jar");
  }

  @Test
  public void testResolveClassPath_invalidCoordinate() throws RepositoryException {
    Artifact nonExistentArtifact = new DefaultArtifact("io.grpc:nosuchartifact:1.2.3");
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(nonExistentArtifact));
    ImmutableList<UnresolvableArtifactProblem> artifactProblems = result.getArtifactProblems();
    Truth.assertThat(artifactProblems).hasSize(1);
    assertEquals(
        "io.grpc:nosuchartifact:jar:1.2.3 was not resolved. Dependency path:"
            + " io.grpc:nosuchartifact:jar:1.2.3 (compile)",
        artifactProblems.get(0).toString());
  }

  @Test
  public void testResolve_emptyInput() throws RepositoryException {
    List<Path> jars = classPathBuilder.resolve(ImmutableList.of()).getClassPath();
    Truth.assertThat(jars).isEmpty();
  }

  @Test
  public void testFindInvalidReferences_selfReferenceFromAbstractClassToInterface()
      throws RepositoryException, IOException {
    List<Path> paths = resolveClassPath("com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha");
    Path httpClientJar =
        paths
            .stream()
            .filter(path -> "httpclient-4.5.3.jar".equals(path.getFileName().toString()))
            .findFirst()
            .get();
    LinkageChecker linkageChecker = LinkageChecker.create(paths, ImmutableSet.copyOf(paths));

    // httpclient-4.5.3 AbstractVerifier has a method reference of
    // 'void verify(String host, String[] cns, String[] subjectAlts)' to itself and its interface
    // X509HostnameVerifier has the method.
    // https://github.com/apache/httpcomponents-client/blob/e2cf733c60f910d17dc5cfc0a77797054a2e322e/httpclient/src/main/java/org/apache/http/conn/ssl/AbstractVerifier.java#L153
    ClassDumper dumper = ClassDumper.create(ImmutableList.of(httpClientJar));

    SymbolReferenceMaps symbolReferenceMaps = dumper.findSymbolReferences();

    Truth.assertWithMessage(
            "httpclient-4.5.3 shoud not contain GZipInputStreamFactory reference, which is added"
                + " 4.5.4")
        .that(symbolReferenceMaps.getClassToClassSymbols())
        .doesNotContainEntry(
            new ClassFile(
                httpClientJar, "org.apache.http.client.protocol.ResponseContentEncoding"),
            new ClassSymbol("org.apache.http.client.entity.GZIPInputStreamFactory"));

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.findSymbolProblems();
    assertEquals(
        "Method references within the same jar file should not be reported",
        0,
        symbolProblems.values().stream()
            .filter(classFile -> httpClientJar.equals(classFile.getJar()))
            .count());
  }

  @Test
  public void testResolveClasspath_notToGenerateRepositoryException() throws RepositoryException {
    List<Path> paths = resolveClassPath("com.google.guava:guava-gwt:jar:20.0");
    Truth.assertThat(paths).isNotEmpty();
  }

  @Test
  public void testResolve_artifactProblems() throws RepositoryException {
    // In the full dependency tree of hibernate-core, xerces-impl:2.6.2 and xml-apis:2.6.2 are not
    // available in Maven Central.
    Artifact hibernateCore = new DefaultArtifact("org.hibernate:hibernate-core:jar:3.5.1-Final");
    ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(hibernateCore));

    ImmutableList<UnresolvableArtifactProblem> artifactProblems = result.getArtifactProblems();

    Truth.assertThat(artifactProblems).hasSize(2);
    assertEquals("xerces:xerces-impl:jar:2.6.2", artifactProblems.get(0).getArtifact().toString());
    assertEquals("xml-apis:xml-apis:jar:2.6.2", artifactProblems.get(1).getArtifact().toString());
  }
}
