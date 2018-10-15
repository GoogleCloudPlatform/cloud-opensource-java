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

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.List;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Formats and prints artifact dependency tree represented by list of {@link DependencyPath}
 */
public class DependencyTreeFormatter {

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Maven coordinate not provided. E.g., 'io.grpc:grpc-auth:1.15.0'");
      return;
    }
    for (String coordinate : args) {
      try {
        printDependencyTree(coordinate);
      } catch (DependencyCollectionException | DependencyResolutionException e) {
        System.err.println(coordinate + " : Failed to retrieve dependency information:"
            + e.getMessage());
      }
    }
  }

  /**
   * Prints dependencies for the coordinate of an artifact
   *
   * @param coordinate Maven coordinate of an artifact to print its dependencies
   * @throws DependencyCollectionException when dependencies cannot be collected
   * @throws DependencyResolutionException when dependencies cannot be resolved
   */
  private static void printDependencyTree(String coordinate)
      throws DependencyCollectionException, DependencyResolutionException {
    DefaultArtifact rootArtifact = new DefaultArtifact(coordinate);
    DependencyGraph dependencyGraph =
        DependencyGraphBuilder.getCompleteDependenciesInPreorder(rootArtifact);
    System.out.println("Dependencies for " + coordinate);
    System.out.println(formatDependencyPaths(dependencyGraph.list()));
  }

  /**
   * Prints dependencies expressed in dependency paths in tree in similar way to mvn
   * dependency:tree.
   *
   * @param dependencyPaths sorted dependency paths in pre-order
   */
  public static String formatDependencyPaths(List<DependencyPath> dependencyPaths) {
    StringBuilder stringBuilder = new StringBuilder();
    ListMultimap<DependencyPath, DependencyPath> tree = buildDependencyPathTree(dependencyPaths);
    // Empty dependency path to retrieve children of root node
    formatDependencyPathTree(stringBuilder, tree, new DependencyPath());
    return stringBuilder.toString();
  }

  private static void formatDependencyPathTree(
      StringBuilder stringBuilder,
      ListMultimap<DependencyPath, DependencyPath> tree,
      DependencyPath currentNode) {
    String indentCharacter = "  ";
    int depth = currentNode.size();
    if (depth > 0) {
      // Nodes at top have one or more depth
      stringBuilder.append(Strings.repeat(indentCharacter, depth));
      stringBuilder.append(currentNode.getLeaf());
      stringBuilder.append("\n");
    }
    for (DependencyPath childPath : tree.get(currentNode)) {
      formatDependencyPathTree(stringBuilder, tree, childPath);
    }
  }

  /**
   * Builds ListMultiMap that represents a Maven dependency tree of parent-children relationship.
   * The root node is represented as an empty {@link DependencyPath} and its children are
   * the values for the root node. As Maven dependency is retrieved via BFS, the order of children
   * matters.
   *
   * @param dependencyPaths a list of dependency path without assuming any order
   * @return ListMultiMap representing a Maven dependency tree
   */
  private static ListMultimap<DependencyPath, DependencyPath> buildDependencyPathTree(
      List<DependencyPath> dependencyPaths) {
    ListMultimap<DependencyPath, DependencyPath> tree = ArrayListMultimap.create();
    for (DependencyPath dependencyPath : dependencyPaths) {
      List<Artifact> artifactPath = dependencyPath.getPath();
      // empty list if the node is at root
      List<Artifact> parentArtifactPath = artifactPath.subList(0, artifactPath.size() - 1);
      DependencyPath parentDependencyPath = new DependencyPath();
      parentArtifactPath.forEach(
          parentArtifactPathNode -> parentDependencyPath.add(parentArtifactPathNode));
      // Relying on DependencyPath's equality
      tree.put(parentDependencyPath, dependencyPath);
    }
    return tree;
  }
}
