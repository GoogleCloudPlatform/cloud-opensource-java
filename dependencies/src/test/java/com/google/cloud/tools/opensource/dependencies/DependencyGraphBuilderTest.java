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
import java.util.HashSet;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.truth.Truth;

public class DependencyGraphBuilderTest {

  @Test
  public void testGetTransitiveDependencies()
      throws DependencyCollectionException, DependencyResolutionException {
    DependencyGraph graph = DependencyGraphBuilder.getTransitiveDependencies("com.google.cloud",
        "google-cloud-datastore", "1.37.1");
    List<DependencyPath> list = graph.list();
    
    Assert.assertTrue(list.size() > 10);
    
    // This method should find Guava exactly once.
    int guavaCount = countGuava(graph);
    Assert.assertEquals(1, guavaCount);
  }
  
  @Test
  public void testGetCompleteDependencies()
      throws DependencyCollectionException, DependencyResolutionException {
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies("com.google.cloud",
        "google-cloud-datastore", "1.37.1");
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
  public void testGetDirectDependencies()
      throws DependencyCollectionException, DependencyResolutionException {
    List<Artifact> artifacts =
        DependencyGraphBuilder.getDirectDependencies("com.google.guava", "guava", "25.1-jre");
    List<String> coordinates = new ArrayList<>();
    for (Artifact artifact : artifacts) {
      coordinates.add(artifact.toString());
    }
    
    Truth.assertThat(coordinates).contains("com.google.code.findbugs:jsr305:jar:3.0.2");
  }
  
  @Test
  public void testGetDirectDependencies_fails() throws DependencyCollectionException {
    try {
      DependencyGraphBuilder.getDirectDependencies("com.google.guava", "guava", "25-1.jre");
      Assert.fail();
    } catch (DependencyResolutionException ex) {
      Assert.assertTrue(ex.getMessage().contains("guava"));
    }
  }
  
  @Test
  public void testGetDirectDependencies_NullGroupId()
      throws DependencyCollectionException, DependencyResolutionException {
    try {
      DependencyGraphBuilder.getDirectDependencies(null, "guava", "25-1.jre");
      Assert.fail();
    } catch (NullPointerException ex) {
      Assert.assertEquals("Group ID cannot be null", ex.getMessage());
    }
  }
  
  @Test
  public void testGetDirectDependencies_nullArtifactId()
      throws DependencyCollectionException, DependencyResolutionException {
    try {
      DependencyGraphBuilder.getDirectDependencies("foo", null, "25-1.jre");
      Assert.fail();
    } catch (NullPointerException ex) {
      Assert.assertEquals("Artifact ID cannot be null", ex.getMessage());
    }
  }
  
  @Test
  public void testGetDirectDependencies_emptyArtifactId()
      throws DependencyCollectionException, DependencyResolutionException {
    try {
      DependencyGraphBuilder.getDirectDependencies("foo", "", "25-1.jre");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertNotNull(ex.getMessage());
    }
  }
  
  @Test
  public void testGetDirectDependencies_nullVersion()
      throws DependencyCollectionException, DependencyResolutionException {
    try {
      DependencyGraphBuilder.getDirectDependencies("foo", "bar", null);
      Assert.fail();
    } catch (NullPointerException ex) {
      Assert.assertEquals("Version cannot be null", ex.getMessage());
    }
  }

}
