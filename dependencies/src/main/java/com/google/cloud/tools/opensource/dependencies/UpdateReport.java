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

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;

class UpdateReport {

  /** 
   * Generate a prioritized and ordered list of 
   * necessary dependency updates. This captures the state of a published
   * artifact in Maven Central. It does not capture updates that may have already been
   * made at head but not published to Maven central, or dependencies
   * that have been updated but not yet incorporated in the tree.
   */
  public static void main(String[] args) throws RepositoryException {

    if (args.length != 1 || !args[0].contains(":")) {
      System.err.println("Usage: java " + UpdateReport.class.getCanonicalName()
          + " groupdId:artifactId:version");
      return;
    }
    
    try {
      DefaultArtifact artifact = new DefaultArtifact(args[0]);

      DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder();
      DependencyGraph graph =
          dependencyGraphBuilder
              .buildFullDependencyGraph(ImmutableList.of(artifact))
              .getDependencyGraph();
      List<Update> updates = graph.findUpdates();
      
      if (updates.isEmpty()) {
        System.out.println(args[0] + " is consistent.");
        System.out.println();        
      } else {
        System.out.println("Upgrades needed for " + args[0] +":");
        System.out.println();
        for (Update update : updates) {
          System.out.println(update);
        }
      }
    } catch (IllegalArgumentException ex) {
      System.err.println("Bad Maven coordinates " + args[0]);
      return;      
    }
  }

}
