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

public class DependencyLister {

  public static void main(String[] args)
      throws DependencyCollectionException, DependencyResolutionException {
    
    if (args.length != 1 || !args[0].contains(":")) {
      System.err.println("Usage: java " + DependencyLister.class.getCanonicalName()
          + " groupdId:artifactId:version");
      return;
    }
    
    String[] coordinates = args[0].split(":");
    String groupId = coordinates[0];
    String artifactId = coordinates[1];
    String version = coordinates[2];
    
    DependencyGraph graph = DependencyGraphBuilder.getCompleteDependencies(groupId, artifactId, version);
    
    List<DependencyPath> paths = graph.list();
    for (DependencyPath path : paths) { 
      System.out.println(path);
    }
  }

}
