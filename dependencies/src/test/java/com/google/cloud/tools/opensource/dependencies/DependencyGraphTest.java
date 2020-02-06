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
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
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

    path1.add(new Dependency(foo, "compile", false));
    path2.add(new Dependency(foo, "compile", false));
    path2.add(new Dependency(bar, "compile", false));
    path3.add(new Dependency(foo, "compile", false));
    path3.add(new Dependency(baz1, "compile", false));
    path4.add(new Dependency(foo, "compile", false));
    path4.add(new Dependency(bar, "compile", false));
    path4.add(new Dependency(baz2, "compile", false));
    path5.add(new Dependency(foo, "compile", false));
    path5.add(new Dependency(bat1, "compile", false));
    path5.add(new Dependency(baz1, "compile", false)); // 2 paths to baz1
    path6.add(new Dependency(foo, "compile", false));
    path6.add(new Dependency(bat1, "compile", false));

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
