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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;

/** Formats Maven artifact dependency tree. */
public class DependencyTreeFormatter {
  /**
   * Formats dependencies as a tree in a similar way to {@code mvn dependency:tree}.
   *
   * @param dependencyPaths dependency paths from @{@link
   *     DependencyGraphBuilder#buildFullDependencyGraph(List)} (Dependency)}.
   */
  static String formatDependencyPaths(List<DependencyPath> dependencyPaths) {
    DependencyPath firstPath = dependencyPaths.get(0);
    Artifact rootArtifact = firstPath.get(0);
    checkArgument(
        dependencyPaths.stream().allMatch(path -> Objects.equals(path.get(0), rootArtifact)),
        "all dependency paths should have the same root");
    StringBuilder stringBuilder = new StringBuilder();
    // While Maven dependencies are resolved in level-order, printing text representing a tree
    // requires traversing the items in pre-order
    ListMultimap<DependencyPath, DependencyPath> tree = buildDependencyPathTree(dependencyPaths);
    // Empty dependency path is to retrieve children of root node
    formatDependencyPathTree(stringBuilder, tree, new DependencyPath(rootArtifact), 1);
    return stringBuilder.toString();
  }

  private static void formatDependencyPathTree(
      StringBuilder stringBuilder,
      ListMultimap<DependencyPath, DependencyPath> tree,
      DependencyPath currentNode,
      int depth) {
    Artifact leaf = currentNode.getLeaf();
    if (leaf != null) {
      // Nodes at top have one or more depth
      stringBuilder.append(Strings.repeat("  ", depth));
      stringBuilder.append(leaf);
      stringBuilder.append("\n");
      depth++;
    }
    for (DependencyPath childPath : tree.get(currentNode)) {
      if (!currentNode.equals(childPath)) { // root node's parent is the root itself
        formatDependencyPathTree(stringBuilder, tree, childPath, depth);
      }
    }
  }

  /**
   * Builds ListMultimap that represents a Maven dependency tree of parent-children relationship.
   * Each node in the tree has a corresponding key (the path from the root to the node) in the
   * ListMultimap. The value associated with that key is a list of the children of the node. The
   * root node is available at the first element in {@code listMultimap.values()}.
   *
   * @param dependencyPaths dependency path instances without assuming any order
   * @return ListMultimap representing a Maven dependency tree of parent-children relationship. Each
   *     node in the tree has a corresponding key in the ListMultimap and the children of the node
   *     are the values for the key in the map. The {@link DependencyPath} representing the root
   *     Maven artifact is available via {@code new DependencyPath(rootArtifact)} where {@code
   *     rootArtifact} is the root of each {@code dependencyPaths}. The root node's parent is the
   *     node itself.
   */
  public static ListMultimap<DependencyPath, DependencyPath> buildDependencyPathTree(
      Collection<DependencyPath> dependencyPaths) {
    // LinkedListMultimap preserves insertion order for values
    ListMultimap<DependencyPath, DependencyPath> tree = LinkedListMultimap.create();
    for (DependencyPath dependencyPath : dependencyPaths) {
      // Relying on DependencyPath's equality
      DependencyPath parentDependencyPath = dependencyPath.getParentPath();

      // Path to root has the same path as its parent
      tree.put(parentDependencyPath, dependencyPath);
    }
    return tree;
  }
}
