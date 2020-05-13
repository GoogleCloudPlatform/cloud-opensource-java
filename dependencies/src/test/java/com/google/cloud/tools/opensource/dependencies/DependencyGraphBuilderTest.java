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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.graph.Traverser;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
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
  public void testGetTransitiveDependencies() {
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildMavenDependencyGraph(new Dependency(datastore, "compile"))
            .getDependencyGraph();
    List<DependencyPath> list = graph.list();

    Assert.assertTrue(list.size() > 10);

    // This method should find Guava exactly once.
    int guavaCount = countGuava(graph);
    Assert.assertEquals(1, guavaCount);
  }

  @Test
  public void testGetCompleteDependencies() {
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildFullDependencyGraph(ImmutableList.of(datastore))
            .getDependencyGraph();
    List<DependencyPath> paths = graph.list();
    Assert.assertTrue(paths.size() > 10);

    // verify we didn't double count anything
    HashSet<DependencyPath> noDups = new HashSet<>(paths);
    Assert.assertEquals(paths.size(), noDups.size());

    // This method should find Guava multiple times, respecting exclusion elements
    int guavaCount = countGuava(graph);
    Assert.assertEquals(29, guavaCount);
  }

  @Test
  public void testGetCompleteDependencies_protobuf() {
    // protobuf-java has only test dependencies.
    // https://search.maven.org/artifact/com.google.protobuf/protobuf-java/3.11.4/bundle
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildFullDependencyGraph(
                ImmutableList.of(new DefaultArtifact("com.google.protobuf:protobuf-java:3.11.4")))
            .getDependencyGraph();
    List<DependencyPath> paths = graph.list();
    Truth.assertThat(paths).hasSize(1);
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
  public void testBuildLinkageCheckDependencyGraph_nonExistentZipDependency() {
    // This artifact depends on log4j-api-java9 (type:zip), which does not exist in Maven central.
    DefaultArtifact log4j2 = new DefaultArtifact("org.apache.logging.log4j:log4j-api:2.11.1");

    // This should not raise DependencyResolutionException
    DependencyGraph completeDependencies =
        dependencyGraphBuilder
            .buildFullDependencyGraph(ImmutableList.of(log4j2))
            .getDependencyGraph();
    Truth.assertThat(completeDependencies.list()).isNotEmpty();
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_multipleArtifacts() {
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildFullDependencyGraph(Arrays.asList(datastore, guava))
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
  public void testSetDetectedOsSystemProperties_netty4Dependency() {
    Artifact nettyArtifact = new DefaultArtifact("io.netty:netty-all:4.1.31.Final");

    // Without system properties "os.detected.arch" and "os.detected.name", this would fail.
    DependencyGraphResult dependencyGraphResult =
        dependencyGraphBuilder.buildMavenDependencyGraph(new Dependency(nettyArtifact, ""));

    Truth.assertThat(dependencyGraphResult.getArtifactProblems()).isEmpty();
    Truth.assertThat(dependencyGraphResult.getDependencyGraph().list()).isNotEmpty();
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_grpcProtobufExclusion() {
    // Grpc-protobuf depends on grpc-protobuf-lite with protobuf-lite exclusion.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/1056
    Artifact grpcProtobuf = new DefaultArtifact("io.grpc:grpc-protobuf:1.25.0");

    DependencyGraph dependencyGraph =
        dependencyGraphBuilder
            .buildFullDependencyGraph(ImmutableList.of(grpcProtobuf))
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
  public void testBuildLinkageCheckDependencyGraph_respectExclusions() {
    // hibernate-core declares jboss-jacc-api_JDK4 dependency excluding jboss-servlet-api_3.0.
    // jboss-jacc-api_JDK4 depends on jboss-servlet-api_3.0:1.0-SNAPSHOT, which is unavailable.
    // DependencyGraphBuilder should respect the exclusion and should not try to download
    // jboss-servlet-api_3.0:1.0-SNAPSHOT.
    Artifact hibernateCore = new DefaultArtifact("org.hibernate:hibernate-core:jar:3.5.1-Final");

    DependencyGraphResult result =
        dependencyGraphBuilder.buildFullDependencyGraph(ImmutableList.of(hibernateCore));

    ImmutableList<UnresolvableArtifactProblem> problems = result.getArtifactProblems();
    for (UnresolvableArtifactProblem problem : problems) {
      Truth.assertThat(problem.toString()).doesNotContain("jboss-servlet-api_3.0");
    }
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_artifactProblems() {
    // In the full dependency tree of hibernate-core, xerces-impl:2.6.2 and xml-apis:2.6.2 are not
    // available in Maven Central.
    Artifact hibernateCore = new DefaultArtifact("org.hibernate:hibernate-core:jar:3.5.1-Final");

    DependencyGraphResult result =
        dependencyGraphBuilder.buildFullDependencyGraph(ImmutableList.of(hibernateCore));

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
  public void testConfigureAdditionalMavenRepositories_addingGoogleAndroidRepository() {
    // Previously this test was using https://repo.spring.io/milestone and artifact
    // org.springframework:spring-asm:3.1.0.RC2 but the repository was not stable.
    DependencyGraphBuilder graphBuilder =
        new DependencyGraphBuilder(ImmutableList.of("https://dl.google.com/dl/android/maven2"));

    // This artifact does not exist in Maven central, but it is in Android repository
    Artifact artifact = new DefaultArtifact("androidx.lifecycle:lifecycle-common-java8:2.0.0");

    // This should not raise an exception
    DependencyGraphResult graph = graphBuilder.buildFullDependencyGraph(ImmutableList.of(artifact));
    assertNotNull(graph.getDependencyGraph());
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_catchRootException() {
    // This should not throw exception
    DependencyGraphResult result =
        dependencyGraphBuilder.buildFullDependencyGraph(
            ImmutableList.of(new DefaultArtifact("ant:ant:jar:1.6.2")));

    ImmutableList<UnresolvableArtifactProblem> problems = result.getArtifactProblems();

    Truth.assertThat(problems)
        .comparingElementsUsing(problemOnArtifact)
        .containsAtLeast("xerces:xerces-impl:2.6.2", "xml-apis:xml-apis:2.6.2");

    Truth.assertThat(problems).hasSize(2);
    Truth.assertThat(problems)
        .comparingElementsUsing(
            Correspondence.transforming(UnresolvableArtifactProblem::toString, "has description"))
        .containsExactly(
            "xerces:xerces-impl:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2"
                + " (compile) > xerces:xerces-impl:jar:2.6.2 (compile?)",
            "xml-apis:xml-apis:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2"
                + " (compile) > xml-apis:xml-apis:jar:2.6.2 (compile?)");
  }

  @Test
  public void testAlts_exclusionElements() {
    Correspondence<DependencyPath, String> dependencyPathToString =
        Correspondence.transforming(DependencyPath::toString, "has string representation");

    DefaultArtifact artifact = new DefaultArtifact("io.grpc:grpc-alts:jar:1.27.0");
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildFullDependencyGraph(ImmutableList.of(artifact))
            .getDependencyGraph();
    List<DependencyPath> dependencyPaths = graph.list();

    String expectedDependencyPathForOpencensusContribHttpUtil =
        "io.grpc:grpc-alts:jar:1.27.0 " // this has exclusion of Guava
            + "/ com.google.auth:google-auth-library-oauth2-http:0.19.0 (compile) "
            + "/ com.google.http-client:google-http-client:1.33.0 (compile) "
            + "/ io.opencensus:opencensus-contrib-http-util:0.24.0 (compile)";

    Truth.assertThat(dependencyPaths)
        .comparingElementsUsing(dependencyPathToString)
        .contains(expectedDependencyPathForOpencensusContribHttpUtil);

    String unexpectedDependencyPathForGuava =
        expectedDependencyPathForOpencensusContribHttpUtil
            + " / com.google.guava:guava:jar:26.0-android (compile)";
    Truth.assertThat(dependencyPaths)
        .comparingElementsUsing(dependencyPathToString)
        .doesNotContain(unexpectedDependencyPathForGuava);
  }

  @Test
  public void testDependencyPathRoot_oneDependency() {
    DependencyGraphResult result =
        dependencyGraphBuilder.buildFullDependencyGraph(
            ImmutableList.of(new DefaultArtifact("com.google.guava:guava:28.1-jre")));
    DependencyPath firstDependencyPath = result.getDependencyGraph().list().get(0);
    assertEquals(
        "com.google.guava:guava:28.1-jre", Artifacts.toCoordinates(firstDependencyPath.get(0)));
  }

  @Test
  public void testDependencyPathRoot_twoDependency() {
    DependencyGraphResult result =
        dependencyGraphBuilder.buildFullDependencyGraph(
            ImmutableList.of(
                new DefaultArtifact("com.google.guava:guava:28.1-jre"),
                new DefaultArtifact("com.google.api:gax:1.57.0")));

    List<DependencyPath> paths = result.getDependencyGraph().list();

    // Because it's requesting a tree with multiple artifacts, the root of the tree is null
    assertNull(paths.get(0).get(0));
    assertEquals(
        "com.google.guava:guava:28.1-jre", Artifacts.toCoordinates(paths.get(0).getLeaf()));

    assertNull(paths.get(1).get(0));
    assertEquals("com.google.api:gax:1.57.0", Artifacts.toCoordinates(paths.get(1).getLeaf()));
  }

  @Test
  public void testResolveCompileTimeDependenciesForBeamHCatalog() {

    System.out.println("This test may take ~50 minutes");

    DependencyNode root = null;
    try {
      // This throws DependencyResolutionException for com.google.inject:guice:jar:no_deps:3.0
      dependencyGraphBuilder.resolveCompileTimeDependencies(
          ImmutableList.of(
              new DefaultDependencyNode(
                  new DefaultArtifact("org.apache.beam:beam-sdks-java-io-hcatalog:2.20.0"))),
          true);
      fail();
    } catch (DependencyResolutionException ex) {
      DependencyResult result = ex.getResult();
      root = result.getRoot();
    }

    int countByGraphTraverser = countByTraverserForGraph(root);
    System.out.println("Count by Traverser.forGraph: " + countByGraphTraverser);

    int countByTreeTraverser = countByTraverserForTree(root);
    System.out.println("Count by Traverser.forTree: " + countByTreeTraverser);

    int countByBfsIdentity = countByBfsObjectIdentity(root);
    System.out.println("Count by BFS with object identity filtering: " + countByBfsIdentity);

    int countByBfs = countByBfs(root);
    System.out.println("Count by BFS: " + countByBfs);

    Truth.assertThat(countByBfs).isLessThan(80_000_000);
  }

  // This method returns 79447 for org.apache.beam:beam-sdks-java-io-hcatalog:2.20.0
  int countByTraverserForGraph(DependencyNode root) {
    // Traverser.forGraph ensures that a node is visited only once
    Traverser<DependencyNode> traverser = Traverser.forGraph(DependencyNode::getChildren);

    Iterable<DependencyNode> nodes = traverser.breadthFirst(root);
    int count = Iterables.size(nodes);
    return count;
  }

  // This method returns 82,572,834 for org.apache.beam:beam-sdks-java-io-hcatalog:2.20.0
  int countByTraverserForTree(DependencyNode root) {
    // Traverser.forTree may visits the same node multiple times if the input is not a tree
    Traverser<DependencyNode> traverser = Traverser.forTree(DependencyNode::getChildren);

    Iterable<DependencyNode> nodes = traverser.breadthFirst(root);
    int count = Iterables.size(nodes);
    return count;
  }

  // This returns 79447 for org.apache.beam:beam-sdks-java-io-hcatalog:2.20.0
  int countByBfsObjectIdentity(DependencyNode root) {
    // Count the number of nodes
    ArrayDeque<DependencyNode> queue = new ArrayDeque<>();
    queue.add(root);
    int count = 0;

    Set<DependencyNode> visited = Sets.newIdentityHashSet();
    while (!queue.isEmpty()) {
      DependencyNode node = queue.poll();
      count++;
      if (count % 1_000_000 == 0) {
        System.out.println("Counting " + count);
      }
      for (DependencyNode child : node.getChildren()) {
        if (visited.add(child)) {
          queue.add(child);
        }
      }
    }
    return count;
  }

  // This method returns 82,572,834 for org.apache.beam:beam-sdks-java-io-hcatalog:2.20.0
  int countByBfs(DependencyNode root) {
    // Count the number of nodes
    ArrayDeque<DependencyNode> queue = new ArrayDeque<>();
    queue.add(root);
    int count = 0;

    while (!queue.isEmpty()) {
      DependencyNode node = queue.poll();
      count++;
      if (count % 1_000_000 == 0) {
        // System.out.println("Counting " + count);
      }
      for (DependencyNode child : node.getChildren()) {
        queue.add(child);
      }
    }
    return count;
  }
}
