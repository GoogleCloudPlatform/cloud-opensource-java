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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.truth.Truth;

public class DependencyGraphTest {

  private DependencyGraph graph = new DependencyGraph();
  private Artifact foo = new DefaultArtifact("com.google:foo:1");
  private Artifact bar = new DefaultArtifact("com.google:bar:1");
  private Artifact baz1 = new DefaultArtifact("com.google:baz:1");
  private Artifact bat1 = new DefaultArtifact("com.google:bat:1");
  private Artifact baz2 = new DefaultArtifact("com.google:baz:2");
  private DependencyPath path1 = new DependencyPath();
  private DependencyPath path2 = new DependencyPath();
  private DependencyPath path3 = new DependencyPath();
  private DependencyPath path4 = new DependencyPath();
  private DependencyPath path5 = new DependencyPath();
  private DependencyPath path6 = new DependencyPath();
  
  @Before
  public void setUp() {
    
    // WARNING the way the path is built here does not necessarily meet 
    // all the preconditions of a path built from a real artifact. In
    // particular, there can be a path to a leaf without including all 
    // the subpaths of that path. 
    
    path1.add(foo);
    path2.add(foo);
    path2.add(bar);
    path3.add(foo);
    path3.add(baz1);
    path4.add(foo);
    path4.add(bar);
    path4.add(baz2);
    path5.add(foo);
    path5.add(bat1);
    path5.add(baz1); // 2 paths to baz1
    path6.add(foo);
    path6.add(bat1);
    
    graph.addPath(path1);
    graph.addPath(path2);
    graph.addPath(path3);
    graph.addPath(path4);
    graph.addPath(path5);
    graph.addPath(path6);
  }
  
  @Test
  public void testGetHighestVersionMap() {
    Map<String, String> map = graph.getHighestVersionMap();
    Assert.assertEquals("2", map.get("com.google:baz"));
    Assert.assertEquals("1", map.get("com.google:foo"));
    Assert.assertEquals("1", map.get("com.google:bar"));
    Assert.assertEquals("1", map.get("com.google:bat"));
    Assert.assertEquals(4, map.keySet().size());
  }
  
  @Test
  public void testFindConflicts() {
    List<DependencyPath> conflicts = graph.findConflicts();
    // TODO should appear in this order but right now doesn't.
    // This may be the easiest test to approach to fix this.
    // Note that even if the manually set order above is not breadth-first
    // conflicts should still appear in the manually set order. That is,
    // we rely on dependencies being built in breadth first order and 
    // not reordered by findConflicts. It appears, however, findConflicts is
    // not preserving order. 
    Truth.assertThat(conflicts).containsExactly(path3, path4, path5); // .inOrder();
  }
  
  
  // TODO next several methods should move to new test class with different fixture
  @Test
  public void testFindUpdates()
      throws DependencyCollectionException, DependencyResolutionException {
    
    DefaultArtifact core =
        new DefaultArtifact("com.google.cloud:google-cloud-core:1.37.1");
    
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(core);
    List<Update> updates = graph.findUpdates();
    List<String> strings = updates.stream().map(e -> e.toString()).collect(Collectors.toList());
    
    // ordering not working yet
    // TODO get order working
    Truth.assertThat(strings).containsExactly("com.google.guava:guava:20.0 needs to "
        + "upgrade com.google.code.findbugs:jsr305:1.3.9 to 3.0.2",
        "com.google.http-client:google-http-client:1.23.0 needs to "
        + "upgrade com.google.code.findbugs:jsr305:1.3.9 to 3.0.2",
        "com.google.api:api-common:1.6.0 needs to "
        + "upgrade com.google.code.findbugs:jsr305:3.0.0 to 3.0.2",
        "com.google.api.grpc:proto-google-common-protos:1.12.0 needs to "
        + "upgrade com.google.protobuf:protobuf-java:3.5.1 to 3.6.0",
        "com.google.api.grpc:proto-google-iam-v1:0.12.0 needs to "
        + "upgrade com.google.protobuf:protobuf-java:3.5.1 to 3.6.0",
        "com.google.api.grpc:proto-google-iam-v1:0.12.0 needs to "
        + "upgrade com.google.api.grpc:proto-google-common-protos:1.11.0 to 1.12.0",
        "com.google.api.grpc:proto-google-iam-v1:0.12.0 needs to "
        + "upgrade com.google.api:api-common:1.5.0 to 1.6.0",
        "com.google.api:api-common:1.6.0 needs to "
        + "upgrade com.google.guava:guava:19.0 to 20.0",
        "com.google.auth:google-auth-library-oauth2-http:0.9.1 needs to "
        + "upgrade com.google.guava:guava:19.0 to 20.0",
        "com.google.protobuf:protobuf-java-util:3.6.0 needs to "
        + "upgrade com.google.guava:guava:19.0 to 20.0",
        "com.google.auth:google-auth-library-oauth2-http:0.9.1 needs to "
        + "upgrade com.google.http-client:google-http-client:1.19.0 to 1.23.0",
        "com.google.http-client:google-http-client-jackson2:1.19.0 needs to "
        + "upgrade com.google.http-client:google-http-client:1.19.0 to 1.23.0");
  }
  
  @Test
  // beam has a more complex dependency graph that hits some corner cases
  public void testFindUpdates_beam()
      throws DependencyCollectionException, DependencyResolutionException {    

    DefaultArtifact beam =
        new DefaultArtifact("org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.5.0");
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(beam);
    
    graph.findUpdates();
  }
  
  @Test
  // a non-Google dependency graph that's well understood and thus useful for debugging
  public void testJaxen()
      throws DependencyCollectionException, DependencyResolutionException {    

    DefaultArtifact jaxen =
        new DefaultArtifact("jaxen:jaxen:1.1.6");
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(jaxen);
    
    List<Update> updates = graph.findUpdates();
    Assert.assertEquals(5, updates.size());
    
    List<DependencyPath> conflicts = graph.findConflicts();
    Assert.assertEquals(33, conflicts.size());
    
    Map<String, String> versions = graph.getHighestVersionMap();
    Assert.assertEquals("2.6.2", versions.get("xerces:xercesImpl"));
  }
  
  @Test
  // a non-Google dependency graph that's well understood and thus useful for debugging
  public void testGrpcAuth()
      throws DependencyCollectionException, DependencyResolutionException {    

    DefaultArtifact grpc = new DefaultArtifact("io.grpc:grpc-auth:1.15.0");
    DependencyGraph completeDependencies = DependencyGraphBuilder.getCompleteDependencies(grpc);
    DependencyGraph transitiveDependencies = DependencyGraphBuilder.getTransitiveDependencies(grpc);
    
    Map<String, String> complete = completeDependencies.getHighestVersionMap();    
    Map<String, String> transitive =
        transitiveDependencies.getHighestVersionMap();
    Set<String> completeKeyset = complete.keySet();
    Set<String> transitiveKeySet = transitive.keySet();
    
    // The complete dependencies sees a path to com.google.j2objc:j2objc-annotations that's
    // been removed in newer versions.
    Assert.assertTrue(completeKeyset.contains("com.google.j2objc:j2objc-annotations"));
    Truth.assertThat(completeKeyset).containsAllIn(transitiveKeySet);
  }
  
  @Test
  public void testFindConflicts_cloudLanguage()
      throws DependencyCollectionException, DependencyResolutionException {
    DefaultArtifact artifact = new DefaultArtifact("com.google.cloud:google-cloud-language:1.37.1");
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(artifact);
    List<DependencyPath> conflicts = graph.findConflicts();
    List<String> leaves = new ArrayList<>();
    for (DependencyPath path : conflicts) {
      leaves.add(Artifacts.toCoordinates(path.getLeaf()));
    }
    
    Truth.assertThat(leaves).containsAllOf(
        "com.google.api.grpc:proto-google-common-protos:1.0.0",
        "com.google.api.grpc:proto-google-common-protos:1.11.0",
        "com.google.api.grpc:proto-google-common-protos:1.12.0");
  }
  
  @Test
  public void testAdd() {
    List<DependencyPath> all = graph.list();
    Truth.assertThat(all).containsExactly(path1, path2, path3, path4, path5, path6).inOrder();
  }
  
  @Test
  public void testGetPaths() {
    Set<DependencyPath> paths = graph.getPaths("com.google:baz:2");
    Truth.assertThat(paths).containsExactly(path4);
  }

  @Test
  public void testGetPaths_multiple() {
    Set<DependencyPath> paths = graph.getPaths("com.google:baz:1");
    Truth.assertThat(paths).containsExactly(path3, path5);
  }

}
