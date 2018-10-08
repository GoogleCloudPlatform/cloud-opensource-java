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
import java.util.List;
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
    DependencyGraph dependencyGraph = DependencyGraphBuilder.getCompleteDependencies(rootArtifact);
    System.out.println("Dependencies for " + coordinate);
    System.out.println(formatDependencyPaths(dependencyGraph.list()));
  }

  /**
   * Prints dependencies expressed in dependency paths in tree in similar way to mvn
   * dependency:tree.
   *
   * @param dependencyPaths sorted dependency paths
   */
  public static String formatDependencyPaths(List<DependencyPath> dependencyPaths) {
    StringBuilder stringBuilder = new StringBuilder();
    for (DependencyPath dependencyPath : dependencyPaths) {
      int depth = dependencyPath.size();
      String indentCharacter = "  ";
      stringBuilder.append(Strings.repeat(indentCharacter, depth) + dependencyPath.getLeaf()+"\n");
    }
    return stringBuilder.toString();
  }
}
