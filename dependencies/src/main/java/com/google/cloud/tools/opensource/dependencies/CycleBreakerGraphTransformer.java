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
import java.util.Set;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Transforms a dependency graph so that it will not contain cycle.
 *
 * <p>A cycle in a dependency graph is a situation where a path to a node from the root contains the
 * same node.
 */
final class CycleBreakerGraphTransformer implements DependencyGraphTransformer {

  @Override
  public DependencyNode transformGraph(
      DependencyNode dependencyNode, DependencyGraphTransformationContext context)
      throws RepositoryException {

    removeCycle(null, dependencyNode, new HashSet<>());
    return dependencyNode;
  }

  private void removeCycle(DependencyNode parent, DependencyNode node, Set<Artifact> parents) {
    Artifact artifact = node.getArtifact();
    if (parents.contains(artifact)) {
      // parent is not null when parents is not empty
      removeChildFromParent(parent, node);
      return;
    }

    parents.add(artifact);
    for (DependencyNode child : node.getChildren()) {
      removeCycle(node, child, parents);
    }
    parents.remove(artifact);
  }

  private void removeChildFromParent(DependencyNode parent, DependencyNode node) {
    ImmutableList<DependencyNode> children =
        parent.getChildren().stream()
            .filter(child -> child != node)
            .collect(ImmutableList.toImmutableList());
    parent.setChildren(children);
  }
}
