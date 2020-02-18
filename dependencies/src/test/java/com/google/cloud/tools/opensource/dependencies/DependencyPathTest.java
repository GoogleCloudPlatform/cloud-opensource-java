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
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Assert;
import org.junit.Test;

public class DependencyPathTest {

  private Artifact foo = new DefaultArtifact("com.google:foo:1");
  private Artifact bar = new DefaultArtifact("com.google:bar:1");
  
  @Test
  public void testSize() {
    DependencyPath path = new DependencyPath();
    Assert.assertEquals(0, path.size());
    path.add(new Dependency(foo, "compile"));
    Assert.assertEquals(1, path.size());
    path.add(new Dependency(bar, "compile"));
    Assert.assertEquals(2, path.size());
  }
  
  @Test
  public void testGetNode() {
    DependencyPath path = new DependencyPath();
    path.add(new Dependency(foo, "compile"));
    path.add(new Dependency(bar, "compile"));
    Assert.assertEquals(foo, path.get(0));
    Assert.assertEquals(bar, path.get(1));
  }

  @Test
  public void testGetParent() {
    DependencyPath path = new DependencyPath();
    path.add(new Dependency(foo, "compile", false));
    path.add(new Dependency(bar, "provided"));
    path.add(new Dependency(foo, "compile"));

    DependencyPath parent = path.getParentPath();

    DependencyPath expected = new DependencyPath();
    expected.add(new Dependency(foo, "compile", false));
    expected.add(new Dependency(bar, "provided"));

    Assert.assertEquals(expected, parent);
  }

  @Test
  public void testGetParent_empty() {
    DependencyPath path = new DependencyPath();

    DependencyPath parent = path.getParentPath();

    Assert.assertEquals(new DependencyPath(), parent);
  }

  @Test
  public void testGetParent_oneElement() {
    DependencyPath path = new DependencyPath();
    path.add(new Dependency(foo, "compile", false));

    DependencyPath parent = path.getParentPath();

    Assert.assertEquals(new DependencyPath(), parent);
  }

  @Test
  public void testToString() {
    DependencyPath path = new DependencyPath();
    path.add(new Dependency(foo, "test", false));
    path.add(new Dependency(bar, "compile", true));
    Assert.assertEquals(
        "com.google:foo:1 (test) / com.google:bar:1 (compile, optional)", path.toString());
  }
  
  @Test
  public void testEquals() {
    DependencyPath path1 = new DependencyPath();
    DependencyPath path2 = new DependencyPath();
    DependencyPath path3 = new DependencyPath();
    DependencyPath path4 = new DependencyPath();

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
  public void testEquals_optionalFlags() {
    DependencyPath pathOptional = new DependencyPath();
    DependencyPath pathFalseOptional = new DependencyPath();
    DependencyPath pathNullOptional = new DependencyPath();

    pathOptional.add(new Dependency(foo, "compile", true));
    pathFalseOptional.add(new Dependency(foo, "compile", false));
    pathNullOptional.add(new Dependency(foo, "compile", null));

    new EqualsTester()
        .addEqualityGroup(pathOptional)
        .addEqualityGroup(pathFalseOptional, pathNullOptional)
        .testEquals();
  }
}
