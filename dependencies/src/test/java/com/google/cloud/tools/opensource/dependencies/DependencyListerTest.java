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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.truth.Truth;

public class DependencyListerTest {

  @Test
  public void testGetTransitiveDependencyPaths()
      throws DependencyCollectionException, DependencyResolutionException {
    DependencyGraph graph =
        DependencyLister.getCompleteDependencies("com.google.cloud", "google-cloud-datastore", "1.37.1");
    Assert.assertTrue(graph.list().size() > 10);
  }
  
  @Test
  public void testGetImmediateDependencies()
      throws DependencyCollectionException, DependencyResolutionException {
    List<Artifact> artifacts =
        DependencyLister.getImmediateDependencies("com.google.guava", "guava", "25.1-jre");
    List<String> coordinates = new ArrayList<>();
    for (Artifact artifact : artifacts) {
      coordinates.add(artifact.toString());
    }
    
    Truth.assertThat(coordinates).contains("com.google.code.findbugs:jsr305:jar:3.0.2");
  }
  
  @Test
  public void testGetImmediateDependencies_fails() throws DependencyCollectionException {
    try {
      DependencyLister.getImmediateDependencies("com.google.guava", "guava", "25-1.jre");
      Assert.fail();
    } catch (DependencyResolutionException ex) {
      Assert.assertTrue(ex.getMessage().contains("guava"));
    }
  }
  
  @Test
  public void testGetImmediateDependencies_NullGroupId()
      throws DependencyCollectionException, DependencyResolutionException {
    try {
      DependencyLister.getImmediateDependencies(null, "guava", "25-1.jre");
      Assert.fail();
    } catch (NullPointerException ex) {
      Assert.assertEquals("Group ID cannot be null", ex.getMessage());
    }
  }
  
  @Test
  public void testGetImmediateDependencies_nullArtifactId()
      throws DependencyCollectionException, DependencyResolutionException {
    try {
      DependencyLister.getImmediateDependencies("foo", null, "25-1.jre");
      Assert.fail();
    } catch (NullPointerException ex) {
      Assert.assertEquals("Artifact ID cannot be null", ex.getMessage());
    }
  }
  
  @Test
  public void testGetImmediateDependencies_emptyArtifactId()
      throws DependencyCollectionException, DependencyResolutionException {
    try {
      DependencyLister.getImmediateDependencies("foo", "", "25-1.jre");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertNotNull(ex.getMessage());
    }
  }
  
  @Test
  public void testGetDependencies_nullVersion()
      throws DependencyCollectionException, DependencyResolutionException {
    try {
      DependencyLister.getImmediateDependencies("foo", "bar", null);
      Assert.fail();
    } catch (NullPointerException ex) {
      Assert.assertEquals("Version cannot be null", ex.getMessage());
    }
  }

}
