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
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;

class DependencyLister {

  public static void main(String[] args) throws RepositoryException {

    if (args.length != 1 || !args[0].contains(":")) {
      System.err.println("Usage: java " + DependencyLister.class.getCanonicalName()
          + " groupdId:artifactId:version");
      return;
    }
    
    try {
      DefaultArtifact artifact = new DefaultArtifact(args[0]);

      DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder();
      DependencyGraph graph =
          dependencyGraphBuilder.getCompleteDependencies(artifact).getDependencyGraph();

      List<DependencyPath> paths = graph.list();
      for (DependencyPath path : paths) { 
        System.out.println(path);
      }
    } catch (IllegalArgumentException ex) {
      System.err.println("Bad Maven coordinates " + args[0]);
      return;      
    }
  }

}
