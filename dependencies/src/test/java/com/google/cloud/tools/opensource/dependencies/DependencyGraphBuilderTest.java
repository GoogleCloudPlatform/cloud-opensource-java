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

package com.google.cloud.tools.opensource.dependencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Assert;
import org.junit.Test;

public class DependencyGraphBuilderTest {

  private DefaultArtifact datastore =
      new DefaultArtifact("com.google.cloud:google-cloud-datastore:1.37.1");
  private DefaultArtifact guava =
      new DefaultArtifact("com.google.guava:guava:25.1-jre");

  private DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder();

  private Correspondence<UnresolvableArtifactProblem, String> problemOnArtifact =
      Correspondence.transforming(
          (UnresolvableArtifactProblem problem) -> Artifacts.toCoordinates(problem.getArtifact()),
          "has artifact");

  @Test
  public void testGetTransitiveDependencies() throws RepositoryException {
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildGraph(new Dependency(datastore, "compile"))
            .getDependencyGraph();
    List<DependencyPath> list = graph.list();

    Assert.assertTrue(list.size() > 10);

    // This method should find Guava exactly once.
    int guavaCount = countGuava(graph);
    Assert.assertEquals(1, guavaCount);
  }

  @Test
  public void testGetCompleteDependencies() throws RepositoryException {
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildCompleteGraph(new Dependency(datastore, "compile"))
            .getDependencyGraph();
    List<DependencyPath> paths = graph.list();
    Assert.assertTrue(paths.size() > 10);

    // verify we didn't double count anything
    HashSet<DependencyPath> noDups = new HashSet<>(paths);
    Assert.assertEquals(paths.size(), noDups.size());

    // This method should find Guava multiple times.
    int guavaCount = countGuava(graph);
    Assert.assertEquals(30, guavaCount);
  }

  private static int countGuava(DependencyGraph graph) {
    int guavaCount = 0;
    for (DependencyPath path : graph.list()) {
      if (path.getLeaf().getArtifactId().equals("guava")) {
        guavaCount++;
      }
    }
    return guavaCount;
  }

  @Test
  public void testGetDirectDependencies() throws RepositoryException {
    List<DependencyNode> nodes =
        dependencyGraphBuilder.getDirectDependencies(new Dependency(guava, ""));
    List<String> coordinates = new ArrayList<>();
    for (DependencyNode node : nodes) {
      coordinates.add(node.getArtifact().toString());
    }

    Truth.assertThat(coordinates).contains("com.google.code.findbugs:jsr305:jar:3.0.2");
  }

  @Test
  public void testGetDirectDependencies_nonExistentZipDependency() throws RepositoryException {
    // This artifact depends on log4j-api-java9 (type:zip), which does not exist in Maven central.
    DefaultArtifact log4j2 = new DefaultArtifact("org.apache.logging.log4j:log4j-api:2.11.1");

    // This should not raise DependencyResolutionException
    DependencyGraph completeDependencies =
        dependencyGraphBuilder
            .buildLinkageCheckDependencyGraph(ImmutableList.of(log4j2))
            .getDependencyGraph();
    Truth.assertThat(completeDependencies.list()).isNotEmpty();
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_multipleArtifacts() throws RepositoryException {
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildLinkageCheckDependencyGraph(Arrays.asList(datastore, guava))
            .getDependencyGraph();

    List<DependencyPath> list = graph.list();
    Assert.assertTrue(list.size() > 10);
    DependencyPath firstElement = list.get(0);
    Assert.assertEquals(
        "Level-order should pick up datastore as first element in the list",
        "google-cloud-datastore",
        firstElement.getLeaf().getArtifactId());
    DependencyPath secondElement = list.get(1);
    Assert.assertEquals(
        "Level-order should pick up guava before the dependencies of the two",
        "guava",
        secondElement.getLeaf().getArtifactId());
  }

  @Test
  public void testSetDetectedOsSystemProperties_netty4Dependency() throws RepositoryException {
    Artifact nettyArtifact = new DefaultArtifact("io.netty:netty-all:4.1.31.Final");

    // Without system properties "os.detected.arch" and "os.detected.name", this would fail.
    List<DependencyNode> nodes = dependencyGraphBuilder.getDirectDependencies(
        new Dependency(nettyArtifact, ""));
    Truth.assertThat(nodes).isNotEmpty();
  }

  @Test
  public void testSetDetectedOsSystemProperties_grpcProtobufExclusion() throws RepositoryException {
    // Grpc-protobuf depends on grpc-protobuf-lite with protobuf-lite exclusion.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/1056
    Artifact grpcProtobuf = new DefaultArtifact("io.grpc:grpc-protobuf:1.25.0");

    DependencyGraph dependencyGraph =
        dependencyGraphBuilder
            .buildLinkageCheckDependencyGraph(ImmutableList.of(grpcProtobuf))
            .getDependencyGraph();

    Correspondence<DependencyPath, String> pathToArtifactKey =
        Correspondence.transforming(
            dependencyPath -> Artifacts.makeKey(dependencyPath.getLeaf()),
            "has a leaf with groupID and artifactID");
    Truth.assertThat(dependencyGraph.list())
        .comparingElementsUsing(pathToArtifactKey)
        .doesNotContain("com.google.protobuf:protobuf-lite");
  }

  @Test
  public void testGetDirectDependencies_respectExclusions() throws RepositoryException {
    // hibernate-core declares jboss-jacc-api_JDK4 dependency excluding jboss-servlet-api_3.0.
    // jboss-jacc-api_JDK4 depends on jboss-servlet-api_3.0:1.0-SNAPSHOT, which is unavailable.
    // DependencyGraphBuilder should respect the exclusion and should not try to download
    // jboss-servlet-api_3.0:1.0-SNAPSHOT.
    Artifact hibernateCore = new DefaultArtifact("org.hibernate:hibernate-core:jar:3.5.1-Final");

    DependencyGraphResult result =
        dependencyGraphBuilder.buildLinkageCheckDependencyGraph(ImmutableList.of(hibernateCore));

    ImmutableList<UnresolvableArtifactProblem> problems = result.getArtifactProblems();
    for (UnresolvableArtifactProblem problem : problems) {
      Truth.assertThat(problem.toString()).doesNotContain("jboss-servlet-api_3.0");
    }
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_artifactProblems() throws RepositoryException {
    // In the full dependency tree of hibernate-core, xerces-impl:2.6.2 and xml-apis:2.6.2 are not
    // available in Maven Central.
    Artifact hibernateCore = new DefaultArtifact("org.hibernate:hibernate-core:jar:3.5.1-Final");

    DependencyGraphResult result =
        dependencyGraphBuilder.buildLinkageCheckDependencyGraph(ImmutableList.of(hibernateCore));

    ImmutableList<UnresolvableArtifactProblem> artifactProblems = result.getArtifactProblems();

    Truth.assertThat(artifactProblems).hasSize(2);
    UnresolvableArtifactProblem firstProblem = artifactProblems.get(0);
    assertEquals("xerces:xerces-impl:jar:2.6.2", firstProblem.getArtifact().toString());

    assertEquals(
        "xerces:xerces-impl:jar:2.6.2 was not resolved. "
            + "Dependency path: org.hibernate:hibernate-core:jar:3.5.1-Final (compile) "
            + "> cglib:cglib:jar:2.2 (compile?) > ant:ant:jar:1.6.2 (compile?) "
            + "> xerces:xerces-impl:jar:2.6.2 (compile?)",
        firstProblem.toString());
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_addingGoogleAndroidRepository()
      throws RepositoryException {
    // Previously this test was using https://repo.spring.io/milestone and artifact
    // org.springframework:spring-asm:3.1.0.RC2 but the repository was not stable.
    DependencyGraphBuilder graphBuilder =
        new DependencyGraphBuilder(ImmutableList.of("https://dl.google.com/dl/android/maven2"));

    // This artifact does not exist in Maven central, but it is in Android repository
    Artifact artifact = new DefaultArtifact("androidx.lifecycle:lifecycle-common-java8:2.0.0");

    // This should not raise an exception
    DependencyGraphResult graph =
        graphBuilder.buildCompleteGraph(new Dependency(artifact, "compile"));
    assertNotNull(graph.getDependencyGraph());
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_notToUseMavenCentral()
      throws RepositoryException {
    DependencyGraphBuilder graphBuilder =
        new DependencyGraphBuilder(ImmutableList.of("https://dl.google.com/dl/android/maven2"));

    // This artifact does not exist in Android's repository
    Artifact artifact = new DefaultArtifact("com.google.guava:guava:28.2-jre");

    DependencyGraphResult result =
        graphBuilder.buildCompleteGraph(new Dependency(artifact, "compile"));
    Truth.assertThat(result.getArtifactProblems())
        .comparingElementsUsing(problemOnArtifact)
        .contains("com.google.guava:guava:28.2-jre");
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_catchRootException() throws RepositoryException {
    // This should not throw exception
    DependencyGraphResult result =
        dependencyGraphBuilder.buildLinkageCheckDependencyGraph(
            ImmutableList.of(new DefaultArtifact("ant:ant:jar:1.6.2")));

    ImmutableList<UnresolvableArtifactProblem> problems = result.getArtifactProblems();

    Truth.assertThat(problems)
        .comparingElementsUsing(problemOnArtifact)
        .containsAtLeast("xerces:xerces-impl:2.6.2", "xml-apis:xml-apis:2.6.2");

    Truth.assertThat(problems).hasSize(4);
    Truth.assertThat(problems)
        .comparingElementsUsing(
            Correspondence.transforming(UnresolvableArtifactProblem::toString, "has description"))
        .containsExactly(
            "xerces:xerces-impl:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2"
                + " (compile) > xerces:xerces-impl:jar:2.6.2 (compile?)",
            "xml-apis:xml-apis:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2"
                + " (compile) > xml-apis:xml-apis:jar:2.6.2 (compile?)",
            "xerces:xerces-impl:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2"
                + " (compile) > xerces:xerces-impl:jar:2.6.2 (compile?) >"
                + " xerces:xerces-impl:jar:2.6.2 (compile?)",
            "xml-apis:xml-apis:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2"
                + " (compile) > xml-apis:xml-apis:jar:2.6.2 (compile?) >"
                + " xml-apis:xml-apis:jar:2.6.2 (compile?)");
  }
}
