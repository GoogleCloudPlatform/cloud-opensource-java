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

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Test;

public class DependencyGraphBuilderTest {

  private DefaultArtifact datastore =
      new DefaultArtifact("com.google.cloud:google-cloud-datastore:1.37.1");
  private DefaultArtifact guava =
      new DefaultArtifact("com.google.guava:guava:25.1-jre");

  @Test
  public void testGetTransitiveDependencies() throws RepositoryException {
    DependencyGraph graph = DependencyGraphBuilder.getTransitiveDependencies(datastore);
    List<DependencyPath> list = graph.list();

    Assert.assertTrue(list.size() > 10);

    // This method should find Guava exactly once.
    int guavaCount = countGuava(graph);
    Assert.assertEquals(1, guavaCount);
  }

  @Test
  public void testGetCompleteDependencies() throws RepositoryException {
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(datastore);
    List<DependencyPath> paths = graph.list();
    Assert.assertTrue(paths.size() > 10);

    // verify we didn't double count anything
    HashSet<DependencyPath> noDups = new HashSet<>(paths);
    Assert.assertEquals(paths.size(), noDups.size());

    // This method should find Guava multiple times.
    int guavaCount = countGuava(graph);
    Assert.assertEquals(31, guavaCount);
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
    List<Artifact> artifacts =
        DependencyGraphBuilder.getDirectDependencies(guava);
    List<String> coordinates = new ArrayList<>();
    for (Artifact artifact : artifacts) {
      coordinates.add(artifact.toString());
    }

    Truth.assertThat(coordinates).contains("com.google.code.findbugs:jsr305:jar:3.0.2");
  }

  @Test
  public void testGetDirectDependencies_nonExistentZipDependency() throws RepositoryException {
    // This artifact depends on log4j-api-java9 (type:zip), which does not exist in Maven central.
    DefaultArtifact log4j2 = new DefaultArtifact("org.apache.logging.log4j:log4j-api:2.11.1");

    // This should not raise DependencyResolutionException
    DependencyGraph completeDependencies =
        DependencyGraphBuilder.getStaticLinkageCheckDependencyGraph(ImmutableList.of(log4j2));
    Truth.assertThat(completeDependencies.list()).isNotEmpty();
  }

  @Test
  public void testGetStaticLinkageCheckDependencyGraph_multipleArtifacts()
      throws RepositoryException {
    DependencyGraph graph =
        DependencyGraphBuilder.getStaticLinkageCheckDependencyGraph(
            Arrays.asList(datastore, guava));

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
    List<Artifact> artifacts = DependencyGraphBuilder.getDirectDependencies(nettyArtifact);
    Truth.assertThat(artifacts).isNotEmpty();
  }

  @Test
  public void testGetStaticLinkageCheckDependencyGraph_ManagedDependency() throws RepositoryException {
    Artifact artifact = new DefaultArtifact("io.grpc:grpc-core:1.24.0");
    DependencyGraph graph = DependencyGraphBuilder
        .getStaticLinkageCheckDependencyGraph(ImmutableList.of(artifact));
    List<DependencyPath> list = graph.list();
    System.out.println(list);

    Optional<Artifact> guava = list.stream()
        .map(DependencyPath::getLeaf)
        .filter(item -> Artifacts.makeKey(item).startsWith("com.google.guava:guava"))
        .findFirst();
    Truth8.assertThat(guava).isPresent();
    Truth.assertThat(guava.get().getVersion()).isEqualTo("28.1-android");
  }

}
