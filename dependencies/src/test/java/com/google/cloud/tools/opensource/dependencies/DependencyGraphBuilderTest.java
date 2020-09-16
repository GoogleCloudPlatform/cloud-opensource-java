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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Assert;
import org.junit.Test;

public class DependencyGraphBuilderTest {

  private final DefaultArtifact logging =
      new DefaultArtifact("commons-logging:commons-logging:1.2");
  private final DefaultArtifact datastore =
      new DefaultArtifact("com.google.cloud:google-cloud-datastore:1.37.1");
  private final DefaultArtifact guava =
      new DefaultArtifact("com.google.guava:guava:25.1-jre");

  private DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder();

  private Correspondence<UnresolvableArtifactProblem, String> problemOnArtifact =
      Correspondence.transforming(
          (UnresolvableArtifactProblem problem) -> Artifacts.toCoordinates(problem.getArtifact()),
          "has artifact");
  
  /**
   * jaxen-core is an optional dependency that should be included when building a dependency graph
   * of JDOM 1.1, no matter how we build the graph.
   */
  @Test
  public void testDirectOptional() {
    DefaultArtifact jdom = new DefaultArtifact("org.jdom:jdom:1.1");

    DependencyGraph fullGraph =
        dependencyGraphBuilder.buildFullDependencyGraph(Arrays.asList(jdom));
    int jaxenCount = countArtifactId(fullGraph, "jaxen-core");
    Assert.assertEquals(1, jaxenCount);
    
    DependencyGraph mavenGraph =
        dependencyGraphBuilder
            .buildMavenDependencyGraph(new Dependency(jdom, "compile"));
    Assert.assertEquals(1, countArtifactId(mavenGraph, "jaxen-core"));   
    
    DependencyGraph verboseGraph =
        dependencyGraphBuilder
            .buildMavenDependencyGraph(new Dependency(jdom, "compile"));
    Assert.assertEquals(1, countArtifactId(verboseGraph, "jaxen-core"));
  }

  @Test
  public void testTransitiveOptional() {
    // an artifact that depends on JDOM 1.1 and not much else
    DefaultArtifact jcommon = new DefaultArtifact("com.decisionlens:jcommon:1.0.0");

    // jaxen-core is an optional dependency of JDOM 1.1.
    // The full dependency graph of JCommon includes it. The Maven dependency graph does not.
    DependencyGraph fullGraph =
        dependencyGraphBuilder.buildFullDependencyGraph(Arrays.asList(jcommon));
    int jaxenCount = countArtifactId(fullGraph, "jaxen-core");
    Assert.assertEquals(1, jaxenCount);
    
    DependencyGraph mavenGraph =
        dependencyGraphBuilder
            .buildMavenDependencyGraph(new Dependency(jcommon, "compile"));
    Assert.assertEquals(0, countArtifactId(mavenGraph, "jaxen-core"));
  }
  
  @Test
  public void testVerboseGraph() {
    // an artifact that depends on JDOM 1.1 and not much else
    DefaultArtifact jcommon = new DefaultArtifact("com.decisionlens:jcommon:1.0.0");

    // jaxen-core is an optional dependency of JDOM 1.1.
    // The unmediated dependency graph does not include it.
    DependencyGraph verboseGraph =
        dependencyGraphBuilder.buildVerboseDependencyGraph(new Dependency(jcommon, "compile"));
    Assert.assertEquals(0, countArtifactId(verboseGraph, "jaxen-core"));    
  }

  @Test
  public void testGetTransitiveDependencies() {
    Dependency dependency = new Dependency(datastore, "compile");
    DependencyGraph graph = dependencyGraphBuilder.buildMavenDependencyGraph(dependency);
    List<DependencyPath> list = graph.list();

    Assert.assertTrue(list.size() > 10);

    // This method should find Guava exactly once.
    int guavaCount = countArtifactId(graph, "guava");
    Assert.assertEquals(1, guavaCount);
  }

  @Test
  public void testGetCompleteDependencies() {
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildFullDependencyGraph(ImmutableList.of(datastore));
    List<DependencyPath> paths = graph.list();
    Assert.assertTrue(paths.size() > 10);

    // verify we didn't double count anything
    Truth.assertThat(paths).containsNoDuplicates();

    // This method should find Guava multiple times, respecting exclusion elements
    int guavaCount = countArtifactId(graph, "guava");
    Assert.assertEquals(29, guavaCount);
  }
  
  @Test
  public void testGetVerboseDependencies() {
    Dependency dependency = new Dependency(datastore, "compile");
    DependencyGraph graph = dependencyGraphBuilder.buildVerboseDependencyGraph(dependency);
    List<DependencyPath> paths = graph.list();
    Assert.assertTrue(paths.size() > 10);

    // verify we didn't double count anything    
    Truth.assertThat(paths).containsNoDuplicates();

    // This method should find Guava multiple times, respecting exclusion elements
    int guavaCount = countArtifactId(graph, "guava");
    Assert.assertEquals(29, guavaCount);
  }

  @Test
  public void testGetCompleteDependencies_protobuf() {
    // protobuf-java has only test dependencies.
    // https://search.maven.org/artifact/com.google.protobuf/protobuf-java/3.11.4/bundle
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildFullDependencyGraph(
                ImmutableList.of(new DefaultArtifact("com.google.protobuf:protobuf-java:3.11.4")));
    List<DependencyPath> paths = graph.list();
    Truth.assertThat(paths).hasSize(1);
  }

  private static int countArtifactId(DependencyGraph graph, String artifactId) {
    int guavaCount = 0;
    for (DependencyPath path : graph.list()) {
      if (path.getLeaf().getArtifactId().equals(artifactId)) {
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
            .buildFullDependencyGraph(ImmutableList.of(log4j2));
    Truth.assertThat(completeDependencies.list()).isNotEmpty();
  }

  @Test
  public void testBuildFullDependencyGraph_multipleArtifacts() {
    DependencyGraph graph =
        dependencyGraphBuilder
            .buildFullDependencyGraph(Arrays.asList(datastore, guava));

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
  public void testBuildVerboseDependencyGraph_singleArtifact() {
    DependencyGraph graph =
        dependencyGraphBuilder.buildVerboseDependencyGraph(logging);

    List<DependencyPath> list = graph.list();
    Assert.assertEquals(
        "commons-logging",
        list.get(0).getLeaf().getArtifactId());
    Assert.assertEquals(
        "log4j",
        list.get(1).getLeaf().getArtifactId());
    Assert.assertEquals(
        "logkit",
        list.get(2).getLeaf().getArtifactId());
    Assert.assertEquals(
        "avalon-framework",
        list.get(3).getLeaf().getArtifactId());
    Assert.assertEquals(
        "servlet-api",
        list.get(4).getLeaf().getArtifactId());
    Assert.assertEquals(
        "javaee-api",
        list.get(5).getLeaf().getArtifactId());
    Assert.assertEquals(6, list.size()); // optional dependencies are included
  }

  @Test
  public void testBuildVerboseDependencyGraph_multipleArtifacts() {
    DependencyGraph graph =
        dependencyGraphBuilder.buildVerboseDependencyGraph(Arrays.asList(logging));

    List<DependencyPath> list = graph.list();
    DependencyPath firstElement = list.get(0);
    Assert.assertEquals(
        "commons-logging",
        firstElement.getLeaf().getArtifactId());
    
    Assert.assertEquals(1, list.size()); // all dependencies are optional
  }

  @Test
  public void testBuildVerboseDependencyGraph_systemScope() {
    DependencyGraph graph =
        dependencyGraphBuilder.buildVerboseDependencyGraph(
            new DefaultArtifact("com.google.guava:guava:29.0-android"));

    for (DependencyPath path : graph.list()) {
      Artifact leaf = path.getLeaf();
      assertNotEquals("srczip", leaf.getArtifactId());
    }
  }

  @Test
  public void testBuildFullDependencyGraph_optional() {
    DependencyGraph graph =
        dependencyGraphBuilder.buildFullDependencyGraph(Arrays.asList(logging));

    List<DependencyPath> list = graph.list();
    Assert.assertEquals(
        "commons-logging",
        list.get(0).getLeaf().getArtifactId());
    Assert.assertEquals(
        "log4j",
        list.get(1).getLeaf().getArtifactId());
    Assert.assertEquals(
        "logkit",
        list.get(2).getLeaf().getArtifactId());
    Assert.assertEquals(
        "avalon-framework",
        list.get(3).getLeaf().getArtifactId());
    Assert.assertEquals(
        "servlet-api",
        list.get(4).getLeaf().getArtifactId());
    Assert.assertEquals(
        "mail",
        list.get(5).getLeaf().getArtifactId());
    Assert.assertEquals(
        "javaee-api",
        list.get(6).getLeaf().getArtifactId());
    Assert.assertEquals(9, list.size()); // optional dependencies are included
  }

  @Test
  public void testSetDetectedOsSystemProperties_netty4Dependency() {
    Artifact nettyArtifact = new DefaultArtifact("io.netty:netty-all:4.1.31.Final");

    // Without system properties "os.detected.arch" and "os.detected.name", this would fail.
    DependencyGraph dependencyGraph =
        dependencyGraphBuilder.buildMavenDependencyGraph(new Dependency(nettyArtifact, ""));

    Truth.assertThat(dependencyGraph.getUnresolvedArtifacts()).isEmpty();
    Truth.assertThat(dependencyGraph.list()).isNotEmpty();
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_grpcProtobufExclusion() {
    // Grpc-protobuf depends on grpc-protobuf-lite with protobuf-lite exclusion.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/1056
    Artifact grpcProtobuf = new DefaultArtifact("io.grpc:grpc-protobuf:1.25.0");

    DependencyGraph dependencyGraph = dependencyGraphBuilder
            .buildFullDependencyGraph(ImmutableList.of(grpcProtobuf));

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

    DependencyGraph result =
        dependencyGraphBuilder.buildFullDependencyGraph(ImmutableList.of(hibernateCore));

    Set<UnresolvableArtifactProblem> problems = result.getUnresolvedArtifacts();
    for (UnresolvableArtifactProblem problem : problems) {
      Truth.assertThat(problem.toString()).doesNotContain("jboss-servlet-api_3.0");
    }
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_artifactProblems() {
    // In the full dependency tree of hibernate-core, xerces-impl:2.6.2 and xml-apis:2.6.2 are not
    // available in Maven Central.
    Artifact hibernateCore = new DefaultArtifact("org.hibernate:hibernate-core:jar:3.5.1-Final");

    DependencyGraph result =
        dependencyGraphBuilder.buildFullDependencyGraph(ImmutableList.of(hibernateCore));

    Set<UnresolvableArtifactProblem> artifactProblems = result.getUnresolvedArtifacts();
    Truth.assertThat(artifactProblems).hasSize(2);

    List<String> errorMessages = artifactProblems.stream()
        .map(x -> x.toString())
        .collect(Collectors.toList());

    Truth.assertThat(errorMessages).contains(
        "xerces:xerces-impl:jar:2.6.2 was not resolved. "
            + "Dependency path: org.hibernate:hibernate-core:jar:3.5.1-Final (compile) "
            + "> cglib:cglib:jar:2.2 (compile?) > ant:ant:jar:1.6.2 (compile?) "
            + "> xerces:xerces-impl:jar:2.6.2 (compile?)");
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
    DependencyGraph graph = graphBuilder.buildFullDependencyGraph(ImmutableList.of(artifact));
    assertNotNull(graph);
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_resolvingMultipleArtifacts() {
    // To verify the effect of the test, you need to cleanup your local Maven repository
    // $ rm -rf  ~/.m2/repository/io/projectreactor  ~/.m2/repository/io/grpc

    DependencyGraphBuilder graphBuilder =
        new DependencyGraphBuilder(ImmutableList.of(
            "https://repo.spring.io/milestone",
            "https://repo.maven.apache.org/maven2"
        ));

    // This artifact is in the Spring Milestones repository.
    Artifact artifactInSpring = new DefaultArtifact("io.projectreactor:reactor-core:3.4.0-M2");

    // This artifact is in the Maven repository.
    Artifact artifactInMaven = new DefaultArtifact("io.grpc:grpc-core:1.25.0");

    // This should not raise an exception
    DependencyGraph graph = graphBuilder.buildVerboseDependencyGraph(ImmutableList.of(
        artifactInMaven, artifactInSpring
    ));

    Truth.assertThat(graph.getUnresolvedArtifacts()).isEmpty();
  }

  @Test
  public void testBuildLinkageCheckDependencyGraph_catchRootException() {
    // This should not throw exception
    DependencyGraph result =
        dependencyGraphBuilder.buildFullDependencyGraph(
            ImmutableList.of(new DefaultArtifact("ant:ant:jar:1.6.2")));

    Set<UnresolvableArtifactProblem> problems = result.getUnresolvedArtifacts();

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
            .buildFullDependencyGraph(ImmutableList.of(artifact));
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
    DependencyGraph result =
        dependencyGraphBuilder.buildFullDependencyGraph(
            ImmutableList.of(new DefaultArtifact("com.google.guava:guava:28.1-jre")));
    DependencyPath firstDependencyPath = result.list().get(0);
    assertEquals(
        "com.google.guava:guava:28.1-jre", Artifacts.toCoordinates(firstDependencyPath.get(0)));
  }

  @Test
  public void testDependencyPathRoot_twoDependency() {
    DependencyGraph result =
        dependencyGraphBuilder.buildFullDependencyGraph(
            ImmutableList.of(
                new DefaultArtifact("com.google.guava:guava:28.1-jre"),
                new DefaultArtifact("com.google.api:gax:1.57.0")));

    List<DependencyPath> paths = result.list();

    // Because it's requesting a tree with multiple artifacts, the root of the tree is null
    assertNull(paths.get(0).get(0));
    assertEquals(
        "com.google.guava:guava:28.1-jre", Artifacts.toCoordinates(paths.get(0).getLeaf()));

    assertNull(paths.get(1).get(0));
    assertEquals("com.google.api:gax:1.57.0", Artifacts.toCoordinates(paths.get(1).getLeaf()));
  }
}
