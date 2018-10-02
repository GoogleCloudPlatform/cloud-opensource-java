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

import java.io.PrintStream;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

class DependencyTreeFormatter {

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Artifact coordinate not provided. E.g., 'io.grpc:grpc-auth:1.15.0'");
      System.exit(1);
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
   * @param coordinate Coordinate of an artifact to print its dependencies
   * @throws DependencyCollectionException when dependencies cannot be collected
   * @throws DependencyResolutionException When dependencies cannot be resolved
   */
  private static void printDependencyTree(String coordinate)
      throws DependencyCollectionException, DependencyResolutionException {
    DefaultArtifact rootArtifact = new DefaultArtifact(coordinate);
    DependencyGraph dependencyGraph = DependencyGraphBuilder.getCompleteDependencies(rootArtifact);
    System.out.println("Dependencies for " + coordinate);
    printDependencyPaths(dependencyGraph.list(), System.out);
  }

  /**
   * Prints dependencies expressed in dependency paths in tree in similar way to mvn dependency:tree
   *
   * @param dependencyPaths sorted dependency paths
   */
  static void printDependencyPaths(List<DependencyPath> dependencyPaths,
      PrintStream outputStream) {
    for (DependencyPath dependencyPath : dependencyPaths) {
      int depth = dependencyPath.size();
      String indentCharacter = "  ";
      outputStream.println(StringUtils.repeat(indentCharacter, depth) + dependencyPath.getLeaf());
    }
  }
}
