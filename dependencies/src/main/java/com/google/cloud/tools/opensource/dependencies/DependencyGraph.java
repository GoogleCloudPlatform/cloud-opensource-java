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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;

/**
 * A representation of the complete non-cyclic transitive dependency tree of a Maven artifact.
 * 
 * <p>Imagine performing a breadth first search through the tree. As we go we build up a list of
 * dependencies. The path to each dependency node is placed in a list. Although each path should
 * appear only once, each dependency may appear many times in different paths. This representation
 * is unusual because it represents a tree as a list of every path from the root to each node,
 * instead of a network of nodes.
 * 
 * <p>Artifacts are considered to be the same if they have the same group ID, artifact ID, and version.
 */
public class DependencyGraph {

  // DependencyGraphBuilder builds this in breadth first order, unless explicitly stated otherwise.
  // That is, this list contains the paths to each node in breadth first order 
  private final List<DependencyPath> graph = new ArrayList<>();

  // map of groupId:artifactId to versions
  // TODO if versions' values were the whole coordinate string 
  // (or even the Artifact itself), would this be simpler?
  private final TreeMultimap<String, String> versions =
      TreeMultimap.create(Comparator.naturalOrder(), new VersionComparator());
  
  // map of groupId:artifactId:version to paths
  private SetMultimap<String, DependencyPath> paths = HashMultimap.create();
  
  @VisibleForTesting
  public DependencyGraph() {
  }

  void addPath(DependencyPath path) {
    graph.add(path);
    Artifact leaf = path.getLeaf();
    String coordinates = Artifacts.toCoordinates(leaf);
    versions.put(Artifacts.makeKey(leaf), leaf.getVersion());
    paths.put(coordinates, path);
  }
  
  /**
   * Returns a list of paths to artifacts in this graph that appear with more than one version.
   * There can be multiple paths to a single version.
   */
  List<DependencyPath> findConflicts() {
    List<DependencyPath> result = new ArrayList<>();
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

  /**
   * @return a mutable copy of the paths in this graph, usually in breadth first order
   */
  public List<DependencyPath> list() {
    return new ArrayList<>(graph);
  }

  /**
   * @return all paths to the specified artifact
   */
  public Set<DependencyPath> getPaths(String coordinates) {
    return paths.get(coordinates);
  }

  /**
   * Returns a list of updates indicating desired updates formatted for a person to read.
   */
  public List<Update> findUpdates() {
    List<DependencyPath> paths = findConflicts();
    
    // now generate necessary upgrades
    LinkedHashSet<Update> upgrades = new LinkedHashSet<>();
    for (DependencyPath path : paths) {
      Artifact leaf = path.getLeaf();
      String key = Artifacts.makeKey(leaf);
      String highestVersion = versions.get(key).last();
      if (!leaf.getVersion().equals(highestVersion)) {
        Artifact parent = path.get(path.size() - 2);
        // when the parent is out of date, update the parent instead
        // TODO drop if any ancestor needs an update, instead of just the parent
        // or perhaps we just order the updates from root down, and then rerun after
        // each fix. Maybe even calculate what will be needed postfix
        String lastParentVersion = versions.get(Artifacts.makeKey(parent)).last();
        if (parent.getVersion().equals(lastParentVersion)) {
          
          // setVersion returns a new instance on change
          Artifact updated = leaf.setVersion(highestVersion);
          Update update = Update.builder()
              .setParent(parent)
              .setFrom(leaf)
              .setTo(updated)
              .build();
          
          upgrades.add(update); 
        }
      }
    }
    
    // TODO sort by path by comparing with the graph
    
    return new ArrayList<>(upgrades);
  }

  /**
   * @return a map of groupId:artifactId to the highest version found in the tree
   */
  public Map<String, String> getHighestVersionMap() {
    Map<String, Collection<String>> input = versions.asMap();
    Map<String, String> output = new HashMap<>();
    
    VersionComparator comparator = new VersionComparator();

    for (Map.Entry<String, Collection<String>> entry : input.entrySet()) {
      String highestVersion = Collections.max(entry.getValue(), comparator);
      output.put(entry.getKey(), highestVersion);
    }
    
    return output;
  }
  
}
