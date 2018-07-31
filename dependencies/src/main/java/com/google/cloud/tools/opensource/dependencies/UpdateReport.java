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

import java.util.List;

import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

public class UpdateReport {

  /** Generate a prioritized and ordered list of 
   * necessary dependency updates. This captures the state of a published
   * artifact in Maven Central. It does not capture updates that may have already been
   * made at head but not published to Maven central, or dependencies
   * that have been updated but not yet incorporated in the tree.
   */
  public static void main(String[] args)
      throws DependencyCollectionException, DependencyResolutionException {
    
    if (args.length != 1 || !args[0].contains(":")) {
      System.err.println("Usage: java " + UpdateReport.class.getCanonicalName()
          + " groupdId:artifactId:version");
      return;
    }
    
    String[] coordinates = args[0].split(":");
    String groupId = coordinates[0];
    String artifactId = coordinates[1];
    String version = coordinates[2];
    
    System.out.println("Upgrades needed for " + args[0] +":");
    System.out.println();
    
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(groupId, artifactId, version);
    List<String> upgrades = graph.findUpdates();
    
    for (String upgrade : upgrades) {
      System.out.println(upgrade);
    }
    
    // todo we need to account for the possibility that an upgrade higher up the path
    // may make an upgrade closer to the leaf moot. Perhaps collect and order them
    // from top of tree down.
  }

}
