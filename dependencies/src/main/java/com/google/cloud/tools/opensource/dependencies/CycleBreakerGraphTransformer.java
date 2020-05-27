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

import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Transforms a dependency graph so that it will not contain cycles.
 *
 * <p>A cycle in a dependency graph is a situation where a path to a node from the root contains the
 * same node. For example, jaxen 1.1-beta-6 is known to have cycle with dom4j 1.6.1.
 */
final class CycleBreakerGraphTransformer implements DependencyGraphTransformer {

  IdentityHashMap<DependencyNode, Boolean> visited = new IdentityHashMap<>();

  @Override
  public DependencyNode transformGraph(
      DependencyNode dependencyNode, DependencyGraphTransformationContext context)
      throws RepositoryException {

    removeCycle(null, dependencyNode, new HashSet<>());
    return dependencyNode;
  }

  private void removeCycle(
      DependencyNode parent, DependencyNode node, Set<Artifact> ancestors) {

    Artifact artifact = node.getArtifact();

    if (ancestors.contains(artifact)) { // Set (rather than List) gives O(1) lookup here
      // parent is not null when ancestors is not empty
      removeChildFromParent(node, parent);
      return;
    }

    if (visited.put(node, true) != null) {
      return;
    }

    ancestors.add(artifact);
    for (DependencyNode child : node.getChildren()) {
      removeCycle(node, child, ancestors);
    }
    ancestors.remove(artifact);
  }

  private static void removeChildFromParent(DependencyNode child, DependencyNode parent) {
    ImmutableList<DependencyNode> children =
        parent.getChildren().stream()
            .filter(node -> node != child)
            .collect(ImmutableList.toImmutableList());
    parent.setChildren(children);
  }
}
