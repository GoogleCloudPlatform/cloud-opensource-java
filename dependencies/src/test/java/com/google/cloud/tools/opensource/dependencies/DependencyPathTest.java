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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Assert;
import org.junit.Test;
import com.google.common.testing.EqualsTester;

public class DependencyPathTest {

  private Artifact foo = new DefaultArtifact("com.google:foo:1");
  private Artifact bar = new DefaultArtifact("com.google:bar:1");
  
  @Test
  public void testSize() {
    DependencyPath path = new DependencyPath();
    Assert.assertEquals(0, path.size());
    path.add(new Dependency(foo, "compile", false));
    Assert.assertEquals(1, path.size());
    path.add(new Dependency(bar, "compile", false));
    Assert.assertEquals(2, path.size());
  }
  
  @Test
  public void testGetNode() {
    DependencyPath path = new DependencyPath();
    path.add(new Dependency(foo, "compile", false));
    path.add(new Dependency(bar, "compile", false));
    Assert.assertEquals(foo, path.get(0));
    Assert.assertEquals(bar, path.get(1));
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

    path1.add(new Dependency(foo, "compile", false));
    path1.add(new Dependency(bar, "compile", false));
    path2.add(new Dependency(foo, "compile", false));
    path2.add(new Dependency(bar, "compile", false));
    path3.add(new Dependency(bar, "compile", false));
    path3.add(new Dependency(foo, "compile", false));
    path4.add(new Dependency(foo, "compile", false));

    new EqualsTester()
        .addEqualityGroup(path1, path2)
        .addEqualityGroup(path3)
        .addEqualityGroup(path4)
        .testEquals();
  }

}
