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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;

/**
 * A representation of the complete non-cyclic transitive dependency tree of 
 * a Maven artifact.
 * 
 * The representation is unusual. Imagine we perform a breadth first search 
 * through the tree. As we go we build up a list of dependencies. The path
 * to eqch dependency node is placed in a list. Although each path should appear only
 * once, each dependency may appear many times in different paths.
 * 
 * Artifacts are considered to be the same if they have the same group ID,
 * artifact ID, and version.
 */
public class DependencyGraph {

  private List<DependencyPath> graph = new ArrayList<>();
  
  // map of groupId:artifactId to versions
  private SetMultimap<String, String> versions = TreeMultimap.create();
  
  // map of groupId:artifactId:version to paths
  private SetMultimap<String, DependencyPath> paths = HashMultimap.create();

  void addPath(DependencyPath path) {
    graph.add(path);
    Artifact leaf = path.getLeaf();
    versions.put(leaf.getGroupId() + ":" + leaf.getArtifactId(), leaf.getVersion());
    paths.put(leaf.getGroupId() + ":" + leaf.getArtifactId() + ":" + leaf.getVersion(), path);
  }
  
  /**
   * Returns a list of paths to artifacts in this graph that appear with more than one version.
   * There can be multiple paths to a single version.
   */
  List<DependencyPath> findConflicts() {
    ArrayList<DependencyPath> result = new ArrayList<>();
    for (String coordinates : versions.keySet()) {
      Set<String> artifactVersions = versions.get(coordinates);
      if (artifactVersions.size() > 1) { // multiple versions
        for (String conflictingVersion : artifactVersions) {
          result.addAll(paths.get(coordinates + ":" + conflictingVersion));
        }
      }
    }
    return result;
  }

  public List<DependencyPath> list() {
    return new ArrayList<>(graph);
  }
  
}
