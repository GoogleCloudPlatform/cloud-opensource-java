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
import com.google.common.testing.EqualsTester;
import com.google.common.truth.Truth;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.junit.Assert;
import org.junit.Test;

public class DependencyPathTest {

  private Artifact root = new DefaultArtifact("a:b:1");
  private Artifact foo = new DefaultArtifact("com.google:foo:1");
  private Artifact bar = new DefaultArtifact("com.google:bar:1");

  DependencyPath pathRootFooBar =
      new DependencyPath(root)
          .append(new Dependency(foo, "test", false))
          .append(new Dependency(bar, "compile", true));

  DependencyPath pathNullRootFooBar =
      new DependencyPath(null)
          .append(new Dependency(foo, "test", false))
          .append(new Dependency(bar, "compile", true));

  @Test
  public void testSize_nullRoot() {
    DependencyPath path = new DependencyPath(null);
    Assert.assertEquals(1, path.size());
    path = path.append(new Dependency(foo, "compile"));
    Assert.assertEquals(2, path.size());
    path = path.append(new Dependency(bar, "compile"));
    Assert.assertEquals(3, path.size());
  }

  @Test
  public void testSize_nonNullRoot() {
    DependencyPath path = new DependencyPath(new DefaultArtifact("a:b:1"));
    Assert.assertEquals(1, path.size());
    path = path.append(new Dependency(foo, "compile"));
    Assert.assertEquals(2, path.size());
    path = path.append(new Dependency(bar, "compile"));
    Assert.assertEquals(3, path.size());
  }

  @Test
  public void testGetNode_nullRoot() {
    DependencyPath path =
        new DependencyPath(null)
            .append(new Dependency(foo, "compile"))
            .append(new Dependency(bar, "compile"));
    Assert.assertNull(path.get(0));
    Assert.assertEquals(foo, path.get(1));
    Assert.assertEquals(bar, path.get(2));
  }

  @Test
  public void testGetNode() {
    DependencyPath path =
        new DependencyPath(new DefaultArtifact("a:b:0.1"))
            .append(new Dependency(foo, "compile"))
            .append(new Dependency(bar, "compile"));
    Assert.assertEquals(new DefaultArtifact("a:b:0.1"), path.get(0));
    Assert.assertEquals(foo, path.get(1));
    Assert.assertEquals(bar, path.get(2));
  }

  @Test
  public void testGetParentPath() {
    DependencyPath parent = pathNullRootFooBar.getParentPath();

    DependencyPath expected = new DependencyPath(null).append(new Dependency(foo, "test", false));

    Assert.assertEquals(expected, parent);
  }

  @Test
  public void testGetParentPath_nonNullRoot() {
    DependencyPath parent = pathRootFooBar.getParentPath();

    DependencyPath expected = new DependencyPath(root).append(new Dependency(foo, "test", false));

    Assert.assertEquals(expected, parent);
  }

  @Test
  public void testGetParentPath_empty_nullRoot() {
    DependencyPath path = new DependencyPath(null);

    DependencyPath parent = path.getParentPath();

    Assert.assertEquals(new DependencyPath(null), parent);
  }

  @Test
  public void testGetParentPath_empty() {
    DependencyPath path = new DependencyPath(new DefaultArtifact("a:b:1"));

    DependencyPath parent = path.getParentPath();

    Assert.assertEquals(new DependencyPath(new DefaultArtifact("a:b:1")), parent);
  }

  @Test
  public void testGetParentPath_oneElement() {
    DependencyPath path = new DependencyPath(null);
    path = path.append(new Dependency(foo, "compile", false));

    DependencyPath parent = path.getParentPath();

    Assert.assertEquals(new DependencyPath(null), parent);
  }

  @Test
  public void testToString_nullRoot() {
    Assert.assertEquals(
        "com.google:foo:1 (test) / com.google:bar:1 (compile, optional)",
        pathNullRootFooBar.toString());
  }

  @Test
  public void testToString() {
    Assert.assertEquals(
        "a:b:jar:1 / com.google:foo:1 (test) / com.google:bar:1 (compile, optional)",
        pathRootFooBar.toString());
  }

  @Test
  public void testToString_singleElement() {
    Assert.assertEquals("a:b:jar:1", new DependencyPath(root).toString());
  }

  @Test
  public void testGetArtifacts() {
    Truth.assertThat(pathRootFooBar.getArtifactKeys())
        .containsExactly("a:b", "com.google:foo", "com.google:bar")
        .inOrder();
  }

  @Test
  public void testGetArtifacts_nullRoot() {
    Truth.assertThat(pathNullRootFooBar.getArtifactKeys())
        .containsExactly("com.google:foo", "com.google:bar")
        .inOrder();
  }

  @Test
  public void testToString_nullOptionalFlag() {
    DependencyPath path =
        new DependencyPath(null)
            .append(new Dependency(foo, "test", false))
            .append(new Dependency(bar, "compile", null));
    Assert.assertEquals("com.google:foo:1 (test) / com.google:bar:1 (compile)", path.toString());
  }

  @Test
  public void testEquals() {
    DependencyPath path1 =
        new DependencyPath(null)
            .append(new Dependency(foo, "compile"))
            .append(new Dependency(bar, "compile"));
    DependencyPath path2 =
        new DependencyPath(null)
            .append(new Dependency(foo, "compile"))
            .append(new Dependency(bar, "compile"));
    DependencyPath path3 =
        new DependencyPath(null)
            .append(new Dependency(bar, "compile"))
            .append(new Dependency(foo, "compile"));
    DependencyPath path4 = new DependencyPath(null).append(new Dependency(foo, "compile"));

    new EqualsTester()
        .addEqualityGroup(path1, path2)
        .addEqualityGroup(path3)
        .addEqualityGroup(path4)
        .testEquals();
  }

  @Test
  public void testEquals_nullOptional() {
    DependencyPath path1 =
        new DependencyPath(null)
            .append(new Dependency(foo, "compile"))
            .append(new Dependency(bar, "compile"));
    DependencyPath path2 =
        new DependencyPath(null)
            .append(new Dependency(foo, "compile"))
            .append(new Dependency(bar, "compile", null));

    new EqualsTester().addEqualityGroup(path1, path2).testEquals();
  }

  @Test
  public void testFindExclusion_exclusionAtNonLeaf() {
    DependencyPath path =
        new DependencyPath(root)
            .append(
                new Dependency(
                    foo, "compile", false, ImmutableList.of(new Exclusion("g1", "a1", null, null))))
            .append(new Dependency(bar, "compile"));

    // The root artifact declares dependency of foo with the exclusion of g1:a1
    Assert.assertEquals(root, path.findExclusion("g1", "a1"));
    Assert.assertNull(path.findExclusion("g1", "abc"));
    Assert.assertNull(path.findExclusion("abc", "a1"));
  }

  @Test
  public void testFindExclusion_exclusionAtLeaf() {
    DependencyPath path =
        new DependencyPath(root)
            .append(new Dependency(foo, "compile"))
            .append(
                new Dependency(
                    bar,
                    "compile",
                    false,
                    ImmutableList.of(new Exclusion("g1", "a1", null, null))));

    // The foo artifact declares dependency of bar with the exclusion of g1:a1
    Assert.assertEquals(foo, path.findExclusion("g1", "a1"));
    Assert.assertNull(path.findExclusion("g1", "abc"));
    Assert.assertNull(path.findExclusion("abc", "a1"));
  }
}
