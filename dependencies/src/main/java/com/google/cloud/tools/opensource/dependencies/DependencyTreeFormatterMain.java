/*
 * Copyright 2019 Google LLC.
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

import static com.google.cloud.tools.opensource.dependencies.DependencyTreeFormatter.formatDependencyPaths;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;

/** Prints artifact dependency tree represented by a list of {@link DependencyPath}s. */
class DependencyTreeFormatterMain {
  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Maven coordinate not provided. E.g., 'io.grpc:grpc-auth:1.15.0'");
      return;
    }
    for (String coordinate : args) {
      try {
        printDependencyTree(coordinate);
      } catch (RepositoryException e) {
        System.err.println(
            coordinate + " : Failed to retrieve dependency information:" + e.getMessage());
      }
    }
  }

  /**
   * Prints dependencies for the coordinate of an artifact
   *
   * @param coordinate Maven coordinate of an artifact to print its dependencies
   */
  private static void printDependencyTree(String coordinate) throws RepositoryException {
    DefaultArtifact rootArtifact = new DefaultArtifact(coordinate);
    DependencyGraph dependencyGraph = DependencyGraphBuilder.getCompleteDependencies(rootArtifact);
    System.out.println("Dependencies for " + coordinate);
    System.out.println(formatDependencyPaths(dependencyGraph.list()));
  }
}
