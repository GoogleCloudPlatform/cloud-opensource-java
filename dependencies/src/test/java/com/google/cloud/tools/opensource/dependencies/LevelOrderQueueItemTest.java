/*
 * Copyright 2020 Google LLC.
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
import org.junit.Test;

public class LevelOrderQueueItemTest {

  @Test
  public void testEquality() {
    Artifact foo = new DefaultArtifact("com.google:foo:1");
    Artifact bar = new DefaultArtifact("com.google:bar:1");
    DependencyPath path1 = new DependencyPath(null).append(new Dependency(foo, "compile"));
    DependencyPath path2 =
        new DependencyPath(null)
            .append(new Dependency(foo, "compile"))
            .append(new Dependency(bar, "compile"));

    new EqualsTester()
        .addEqualityGroup(new PathToNode<>(1, path1), new PathToNode<>(1, path1))
        .addEqualityGroup(new PathToNode<>(1, path2))
        .addEqualityGroup(new PathToNode<>(2, path2), new PathToNode<>(2, path2))
        .testEquals();
  }
}
