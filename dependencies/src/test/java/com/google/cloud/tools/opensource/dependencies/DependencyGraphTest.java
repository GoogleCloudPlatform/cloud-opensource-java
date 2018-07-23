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

import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Test;

public class DependencyGraphTest {

  private DependencyGraph graph = new DependencyGraph();
  private Artifact foo = new DefaultArtifact("com.google:foo:1");
  private Artifact bar = new DefaultArtifact("com.google:bar:1");
  private Artifact baz1 = new DefaultArtifact("com.google:baz:1");
  private Artifact baz2= new DefaultArtifact("com.google:baz:2");
  
  
  @Test
  public void testAdd() {
    
    DependencyPath path1 = new DependencyPath();
    path1.add(foo);
    DependencyPath path2 = new DependencyPath();
    path2.add(foo);
    path2.add(bar);
    DependencyPath path3 = new DependencyPath();
    path3.add(foo);
    path3.add(baz1);
    DependencyPath path4 = new DependencyPath();
    path4.add(foo);
    path4.add(bar);
    path4.add(baz2);
    
    graph.addPath(path1);
    graph.addPath(path2);
    graph.addPath(path3);
    graph.addPath(path4);
    
    List<DependencyPath> conflicts = graph.findConflicts();
    Assert.assertEquals(2, conflicts.size());
  }

}
