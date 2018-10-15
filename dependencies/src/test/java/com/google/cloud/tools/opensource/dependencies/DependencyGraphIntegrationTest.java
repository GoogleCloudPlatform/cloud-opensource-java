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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.truth.Truth;

/**
 *  Tests against actual artifacts in Maven central.
 */
public class DependencyGraphIntegrationTest {

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
  
  // Beam has a more complex dependency graph that hits some corner cases.
  // In particular it pulls in Netty, which pulls in native code, the 
  // exact artifact depending on which operating system you're running on.
  // This tests verifies that DependencyGraphBuilder sets the os.detected.classifier
  // system property. Take that out and this test will fail while others still pass. 
  @Test
  public void testFindUpdates_beam()
      throws DependencyCollectionException, DependencyResolutionException {    

    DefaultArtifact beam =
        new DefaultArtifact("org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.5.0");
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(beam);
    
    // should not throw
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
    Truth.assertThat(updates).hasSize(5);
    
    List<DependencyPath> conflicts = graph.findConflicts();
    Truth.assertThat(conflicts).hasSize(30);
    
    Map<String, String> versions = graph.getHighestVersionMap();
    Assert.assertEquals("2.6.2", versions.get("xerces:xercesImpl"));
  }
  
  @Test
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
    // been removed in newer versions so this is not bidirectional set equality.
    Assert.assertTrue(complete.containsKey("com.google.j2objc:j2objc-annotations"));
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

}
