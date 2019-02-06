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

import com.google.cloud.tools.opensource.dependencies.AggregatedRepositoryException;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.ExceptionAndPath;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Assert;
import org.junit.Test;

public class ClassPathBuilderTest {

  static final Correspondence<Path, String> PATH_FILE_NAMES =
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

  @Test
  public void testArtifactsToPaths_removingDuplicates() throws RepositoryException {
    Artifact grpcArtifact = new DefaultArtifact("io.grpc:grpc-auth:1.15.1");
    ListMultimap<Path, DependencyPath> multimap =
        ClassPathBuilder.artifactsToDependencyPaths(ImmutableList.of(grpcArtifact));

    Set<Path> paths = multimap.keySet();
    long jsr305Count = paths.stream().filter(path -> path.toString().contains("jsr305-")).count();
    Truth.assertWithMessage("There should not be duplicated versions for jsr305")
        .that(jsr305Count)
        .isEqualTo(1);

    Optional<Path> opencensusApiPathFound =
        paths.stream().filter(path -> path.toString().contains("opencensus-api-")).findFirst();
    Truth8.assertThat(opencensusApiPathFound).isPresent();
    Path opencensusApiPath = opencensusApiPathFound.get();
    Truth.assertWithMessage("Opencensus API should have multiple dependency paths")
        .that(multimap.get(opencensusApiPath).size())
        .isGreaterThan(1);
  }

  @Test
  public void testBomToPaths_firstElementsAreBomMembers() throws RepositoryException {
    DefaultArtifact bom =
        new DefaultArtifact("com.google.cloud:cloud-oss-bom:pom:1.0.0-SNAPSHOT");
    List<Artifact> managedDependencies = RepositoryUtility.readBom(bom);

    LinkedListMultimap<Path, DependencyPath> jarToDependencyPaths =
        ClassPathBuilder.artifactsToDependencyPaths(managedDependencies);

    ImmutableList<Path> paths = ImmutableList.copyOf(jarToDependencyPaths.keySet());

    Truth.assertThat(paths.get(0).getFileName().toString()).isEqualTo(
        "guava-26.0-android.jar"); // first element in the BOM
    int bomSize = managedDependencies.size();
    Truth.assertThat(paths.get(bomSize - 1).getFileName().toString()).isEqualTo(
        "gax-httpjson-0.52.1.jar"); // last element in the BOM
  }

  @Test
  public void testArtifactsToPaths() throws RepositoryException {

    Artifact grpcArtifact = new DefaultArtifact("io.grpc:grpc-auth:1.15.1");
    ListMultimap<Path, DependencyPath> multimap =
        ClassPathBuilder.artifactsToDependencyPaths(ImmutableList.of(grpcArtifact));

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
    List<Path> paths = ClassPathBuilder.artifactsToClasspath(ImmutableList.of(grpcArtifact));

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
        ClassPathBuilder.artifactsToClasspath(ImmutableList.of(bigTableArtifact));
    Truth.assertThat(paths).comparingElementsUsing(PATH_FILE_NAMES).contains("log4j-1.2.12.jar");
  }

  @Test
  public void testCoordinateToClasspath_invalidCoordinate() {
    Artifact nonExistentArtifact = new DefaultArtifact("io.grpc:nosuchartifact:1.2.3");
    try {
      ClassPathBuilder.artifactsToClasspath(ImmutableList.of(nonExistentArtifact));
      Assert.fail("Invalid Maven coodinate should raise RepositoryException");
    } catch (RepositoryException ex) {
      Truth.assertThat(ex.getMessage())
          .contains("Could not find artifact io.grpc:nosuchartifact:jar:1.2.3");
    }
  }

  @Test
  public void testCoordinateToClasspath_emptyInput() throws RepositoryException {
    List<Path> jars = ClassPathBuilder.artifactsToClasspath(ImmutableList.of());
    Truth.assertThat(jars).isEmpty();
  }

  @Test
  public void testFindInvalidReferences_selfReferenceFromAbstractClassToInterface()
      throws RepositoryException, IOException {
    Artifact bigTableArtifact =
        new DefaultArtifact("com.google.cloud:google-cloud-bigtable:jar:0.66.0-alpha");
    List<Path> paths =
        ClassPathBuilder.artifactsToClasspath(ImmutableList.of(bigTableArtifact));
    Path httpClientJar =
        paths
            .stream()
            .filter(path -> "httpclient-4.5.3.jar".equals(path.getFileName().toString()))
            .findFirst()
            .get();
    ClasspathChecker classpathChecker = ClasspathChecker.create(paths, ImmutableSet.copyOf(paths));

    // httpclient-4.5.3 AbstractVerifier has a method reference of
    // 'void verify(String host, String[] cns, String[] subjectAlts)' to itself and its interface
    // X509HostnameVerifier has the method.
    // https://github.com/apache/httpcomponents-client/blob/e2cf733c60f910d17dc5cfc0a77797054a2e322e/httpclient/src/main/java/org/apache/http/conn/ssl/AbstractVerifier.java#L153
    SymbolReferenceSet symbolReferenceSet = ClassDumper.scanSymbolReferencesInJar(httpClientJar);
    ClassSymbolReference referenceToGZipInputStreamFactory =
        ClassSymbolReference.builder()
            .setSourceClassName("org.apache.http.client.protocol.ResponseContentEncoding")
            .setSubclass(false)
            .setTargetClassName("org.apache.http.client.entity.GZIPInputStreamFactory")
            .build();
    if (symbolReferenceSet.getClassReferences().contains(referenceToGZipInputStreamFactory)) {
      System.out.println(
          "Somehow httpclient-4.5.3 contains GZipInputStreamFactory reference, which is added 4.5.4");
    }

    JarLinkageReport jarLinkageReport = classpathChecker.generateLinkageReport(httpClientJar,
        symbolReferenceSet);

    Truth.assertWithMessage("Method references within the same jar file should not be reported")
        .that(jarLinkageReport.getMissingMethodErrors())
        .isEmpty();
  }

  @Test
  public void testArtifactToClasspath_notToGenerateRepositoryException()
      throws RepositoryException {
    Artifact jamonApiArtifact = new DefaultArtifact("com.google.guava:guava-gwt:jar:20.0");
    List<Path> paths = ClassPathBuilder.artifactsToClasspath(ImmutableList.of(jamonApiArtifact));
    Truth.assertThat(paths).isNotEmpty();
  }

  @Test
  public void testArtifactToClasspath_reportAggregatedRepositoryException()
      throws RepositoryException {
    // jamon has transitive dependency to jmxtools, which does not exist in Maven central
    Artifact jamonApiArtifact = new DefaultArtifact("com.jamonapi:jamon:2.81");
    try {
      ClassPathBuilder.artifactsToClasspath(ImmutableList.of(jamonApiArtifact));
      Assert.fail();
    } catch (AggregatedRepositoryException ex) {
      ImmutableList<ExceptionAndPath> failures = ex.getUnderlyingFailures();
      Truth.assertThat(failures).isNotEmpty();
      long jmxToolFailureCount = failures.stream().filter(
          failure -> {
            List<DependencyNode> path = failure.getPath();
            return "jmxtools".equals(path.get(path.size()-1).getArtifact().getArtifactId());
          }
      ).count();

      Truth.assertThat(jmxToolFailureCount).isEqualTo(1);
    }
  }
}
