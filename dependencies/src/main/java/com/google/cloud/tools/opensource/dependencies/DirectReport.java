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

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

class DirectReport {

  /**
   * Generate a list of direct dependencies. This captures the state of a published
   * artifact in Maven Central. It does not capture updates that may have already been
   * made at head but not published to Maven central, or dependencies
   * that have been updated but not yet incorporated in the tree.
   */
  public static void main(String[] args) throws RepositoryException {
    
    if (args.length != 1 || !args[0].contains(":")) {
      System.err.println("Usage: java " + DirectReport.class.getCanonicalName()
          + " groupdId:artifactId:version");
      return;
    }
    
    System.out.println("Dependencies of " + args[0] +":");
    System.out.println();
    
    Artifact input = new DefaultArtifact(args[0]);
    DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder();
    DependencyGraphResult dependencyGraphResult =
        dependencyGraphBuilder.buildMavenDependencyGraph(new Dependency(input, ""));

    for (DependencyPath dependencyPath : dependencyGraphResult.getDependencyGraph().list()) {
      if (dependencyPath.size() != 2) {
        continue;
      }
      Artifact artifact = dependencyPath.getLeaf();
      System.out.println("  <dependency>");
      System.out.println("    <groupId>" + artifact.getGroupId() + "</groupId>");
      System.out.println("    <artifactId>" + artifact.getArtifactId() + "</artifactId>");
      System.out.println("    <version>" + artifact.getVersion() + "</version>");
      System.out.println("  </dependency>");
    }
  }

}
