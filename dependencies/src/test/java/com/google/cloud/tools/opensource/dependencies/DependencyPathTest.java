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

import com.google.common.testing.EqualsTester;
import javax.enterprise.inject.Default;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Assert;
import org.junit.Test;

public class DependencyPathTest {

  private Artifact foo = new DefaultArtifact("com.google:foo:1");
  private Artifact bar = new DefaultArtifact("com.google:bar:1");
  
  @Test
  public void testSize_nullRoot() {
    DependencyPath path = new DependencyPath(null);
    Assert.assertEquals(1, path.size());
    path.add(new Dependency(foo, "compile"));
    Assert.assertEquals(2, path.size());
    path.add(new Dependency(bar, "compile"));
    Assert.assertEquals(3, path.size());
  }

  @Test
  public void testSize_nonNullRoot() {
    DependencyPath path = new DependencyPath(new DefaultArtifact("a:b:1"));
    Assert.assertEquals(1, path.size());
    path.add(new Dependency(foo, "compile"));
    Assert.assertEquals(2, path.size());
    path.add(new Dependency(bar, "compile"));
    Assert.assertEquals(3, path.size());
  }
  
  @Test
  public void testGetNode() {
    DependencyPath path = new DependencyPath(null);
    path.add(new Dependency(foo, "compile"));
    path.add(new Dependency(bar, "compile"));
    Assert.assertEquals(foo, path.get(0));
    Assert.assertEquals(bar, path.get(1));
  }

  @Test
  public void testGetParentPath() {
    DependencyPath path = new DependencyPath(null);
    path.add(new Dependency(foo, "compile", false));
    path.add(new Dependency(bar, "provided"));
    path.add(new Dependency(foo, "compile"));

    DependencyPath parent = path.getParentPath();

    DependencyPath expected = new DependencyPath(null);
    expected.add(new Dependency(foo, "compile", false));
    expected.add(new Dependency(bar, "provided"));

    Assert.assertEquals(expected, parent);
  }

  @Test
  public void testGetParentPath_nonNullRoot() {
    Artifact root = new DefaultArtifact("a:b:1");
    DependencyPath path = new DependencyPath(root);
    path.add(new Dependency(foo, "compile", false));
    path.add(new Dependency(bar, "provided"));
    path.add(new Dependency(foo, "compile"));

    DependencyPath parent = path.getParentPath();

    DependencyPath expected = new DependencyPath(root);
    expected.add(new Dependency(foo, "compile", false));
    expected.add(new Dependency(bar, "provided"));

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
    path.add(new Dependency(foo, "compile", false));

    DependencyPath parent = path.getParentPath();

    Assert.assertEquals(new DependencyPath(null), parent);
  }

  @Test
  public void testToString() {
    DependencyPath path = new DependencyPath(null);
    path.add(new Dependency(foo, "test", false));
    path.add(new Dependency(bar, "compile", true));
    Assert.assertEquals(
        "com.google:foo:1 (test) / com.google:bar:1 (compile, optional)", path.toString());
  }

  @Test
  public void testToString_nullOptionalFlag() {
    DependencyPath path = new DependencyPath(null);
    path.add(new Dependency(foo, "test", false));
    path.add(new Dependency(bar, "compile", null));
    Assert.assertEquals("com.google:foo:1 (test) / com.google:bar:1 (compile)", path.toString());
  }

  @Test
  public void testEquals() {
    DependencyPath path1 = new DependencyPath(null);
    DependencyPath path2 = new DependencyPath(null);
    DependencyPath path3 = new DependencyPath(null);
    DependencyPath path4 = new DependencyPath(null);

    path1.add(new Dependency(foo, "compile"));
    path1.add(new Dependency(bar, "compile"));
    path2.add(new Dependency(foo, "compile"));
    path2.add(new Dependency(bar, "compile"));
    path3.add(new Dependency(bar, "compile"));
    path3.add(new Dependency(foo, "compile"));
    path4.add(new Dependency(foo, "compile"));

    new EqualsTester()
        .addEqualityGroup(path1, path2)
        .addEqualityGroup(path3)
        .addEqualityGroup(path4)
        .testEquals();
  }

  @Test
  public void testEquals_nullOptional() {
    DependencyPath path1 = new DependencyPath(null);
    DependencyPath path2 = new DependencyPath(null);

    path1.add(new Dependency(foo, "compile"));
    path1.add(new Dependency(bar, "compile"));

    path2.add(new Dependency(foo, "compile"));
    path2.add(new Dependency(bar, "compile", null));

    new EqualsTester().addEqualityGroup(path1, path2).testEquals();
  }
}
