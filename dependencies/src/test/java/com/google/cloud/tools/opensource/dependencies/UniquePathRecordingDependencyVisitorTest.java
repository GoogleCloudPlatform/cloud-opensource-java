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

import static org.junit.Assert.assertSame;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.util.List;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

public class UniquePathRecordingDependencyVisitorTest {

  @Test
  public void testVisitingUniqueNodes_twoPaths() {
    // This setup creates a dependency graph like below. There are two paths from the root to node
    // 'x': root-a-x and root-b-x. The two paths do not share intermediate nodes.
    //
    //    root
    //   /   \
    //  a     b
    //   \   /
    //     x

    DefaultDependencyNode root = new DefaultDependencyNode(new DefaultArtifact("g:r:1"));
    DefaultDependencyNode a = new DefaultDependencyNode(new DefaultArtifact("g:a:1"));
    DefaultDependencyNode b = new DefaultDependencyNode(new DefaultArtifact("g:b:1"));
    DefaultDependencyNode x = new DefaultDependencyNode(new DefaultArtifact("g:x:1"));

    root.setChildren(ImmutableList.of(a, b));
    a.setChildren(ImmutableList.of(x));
    b.setChildren(ImmutableList.of(x));

    UniquePathRecordingDependencyVisitor visitor =
        new UniquePathRecordingDependencyVisitor(
            (DependencyNode node, List<DependencyNode> parents) ->
                node.getArtifact().getArtifactId().equals("x"));

    root.accept(visitor);

    ImmutableList<List<DependencyNode>> paths = visitor.getPaths();
    Truth.assertThat(paths).hasSize(2);
    assertSame(root, paths.get(0).get(0));
    assertSame(a, paths.get(0).get(1));
    assertSame(x, paths.get(0).get(2));

    assertSame(root, paths.get(1).get(0));
    assertSame(b, paths.get(1).get(1));
    assertSame(x, paths.get(1).get(2));
  }

  @Test
  public void testVisitingUniqueNodes() {
    // This setup creates a dependency graph like below. There are two paths from the root to node
    // 'y'. UniquePathRecordingDependencyVisitor does not record paths that have the same
    // intermediate node.
    //
    //    root
    //   /   \
    //  a     b
    //   \   /
    //     x
    //     |
    //     y

    DefaultDependencyNode root = new DefaultDependencyNode(new DefaultArtifact("g:r:1"));
    DefaultDependencyNode a = new DefaultDependencyNode(new DefaultArtifact("g:a:1"));
    DefaultDependencyNode b = new DefaultDependencyNode(new DefaultArtifact("g:b:1"));
    DefaultDependencyNode x = new DefaultDependencyNode(new DefaultArtifact("g:x:1"));
    DefaultDependencyNode y = new DefaultDependencyNode(new DefaultArtifact("g:y:1"));

    root.setChildren(ImmutableList.of(a, b));
    a.setChildren(ImmutableList.of(x));
    b.setChildren(ImmutableList.of(x));
    x.setChildren(ImmutableList.of(y));

    UniquePathRecordingDependencyVisitor visitor =
        new UniquePathRecordingDependencyVisitor(
            (DependencyNode node, List<DependencyNode> parents) ->
                node.getArtifact().getArtifactId().equals("y"));

    root.accept(visitor);

    ImmutableList<List<DependencyNode>> paths = visitor.getPaths();
    Truth.assertThat(paths).hasSize(1);
    assertSame(root, paths.get(0).get(0));
    assertSame(a, paths.get(0).get(1));
    assertSame(x, paths.get(0).get(2));
    assertSame(y, paths.get(0).get(3));
  }
}
