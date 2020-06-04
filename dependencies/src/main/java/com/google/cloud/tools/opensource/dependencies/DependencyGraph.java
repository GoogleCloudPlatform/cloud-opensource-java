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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;

/**
 * A complete non-cyclic transitive dependency graph of a Maven dependency.
 * 
 * <p>Imagine performing a breadth first search starting with a given dependency
 * and continuing through its dependencies, and accumulating the path to each node
 * from the root as a list of dependencies. Although each path should
 * appear only once, each dependency may appear many times in different paths. This representation
 * is unusual because it represents a tree as a list of every path from the root to each node,
 * instead of a network of nodes.
 * 
 * <p>Artifacts are considered to be the same if they have the same group ID, artifact ID, and version.
 */
public class DependencyGraph {

  private static final class LevelOrderQueueItem {
    final DependencyNode dependencyNode;
  
    // Null for the first item
    final DependencyPath parentPath;
  
    LevelOrderQueueItem(DependencyNode dependencyNode, DependencyPath parentPath) {
      this.dependencyNode = dependencyNode;
      this.parentPath = parentPath;
    }
  }

  // DependencyGraphBuilder builds this in breadth first order, unless explicitly stated otherwise.
  // That is, this list contains the paths to each node in breadth first order 
  private final List<DependencyPath> graph = new ArrayList<>();
  
  private final Set<UnresolvableArtifactProblem> artifactProblems = new HashSet<>();

  // map of groupId:artifactId to versions
  // TODO if versions' values were the whole coordinate string 
  // (or even the Artifact itself), would this be simpler?
  private final TreeMultimap<String, String> versions =
      TreeMultimap.create(Comparator.naturalOrder(), new VersionComparator());
  
  // map of groupId:artifactId:version to paths
  private SetMultimap<String, DependencyPath> paths = HashMultimap.create();

  private DependencyNode root;

  public DependencyGraph(DependencyNode root) {
    this.root = root;
  }

  void addPath(DependencyPath path) {
    Artifact leaf = path.getLeaf();
    if (leaf == null) {
      // No need to include a path to null leaf
      return;
    }
    graph.add(path);
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

  /**
   * Returns artifacts that could not be resolved during the construction of this graph. 
   */
  public Set<UnresolvableArtifactProblem> getUnresolvedArtifacts() {
    return new HashSet<>(artifactProblems);
  }
  
  /**
   * Creates a problem describing that {@code artifact} is unresolvable in this
   * dependency graph.
   */
  public UnresolvableArtifactProblem createUnresolvableArtifactProblem(Artifact artifact) {
    ImmutableList<List<DependencyNode>> paths = findArtifactPaths(root, artifact);
    if (paths.isEmpty()) {
      // On certain conditions, Maven throws ArtifactDescriptorException even when the
      // (transformed) dependency graph does not contain the problematic artifact any more.
      // https://issues.apache.org/jira/browse/MNG-6732
      return new UnresolvableArtifactProblem(artifact);
    } else {
      return new UnresolvableArtifactProblem(paths.get(0));
    }
  }

  private static ImmutableList<List<DependencyNode>> findArtifactPaths(
      DependencyNode root, Artifact artifact) {
    String coordinates = Artifacts.toCoordinates(artifact);
    DependencyFilter filter =
        (node, parents) ->
            node.getArtifact() != null // artifact is null at a root dummy node.
                && Artifacts.toCoordinates(node.getArtifact()).equals(coordinates);
    UniquePathRecordingDependencyVisitor visitor = new UniquePathRecordingDependencyVisitor(filter);
    root.accept(visitor);
    return ImmutableList.copyOf(visitor.getPaths());
  }

  private final Set<Artifact> checkedArtifacts = new HashSet<>();
  
  void addUnresolvableArtifactProblem(Artifact artifact) {
    if (checkedArtifacts.add(artifact)) {
      artifactProblems.add(createUnresolvableArtifactProblem(artifact));
    }
  }

  /**
   * Builds a dependency graph by traversing dependency tree in level-order (breadth-first search).
   *
   * @param root node to start traversal
   */
  public static DependencyGraph from(DependencyNode root) {
    DependencyGraph graph = new DependencyGraph(root);
  
    return levelOrder(graph);
  }

  // a bit weird; this modifies the argument and returns it
  static DependencyGraph levelOrder(DependencyGraph graph) {
    Queue<DependencyGraph.LevelOrderQueueItem> queue = new ArrayDeque<>();
    queue.add(new DependencyGraph.LevelOrderQueueItem(graph.root, null));
  
    while (!queue.isEmpty()) {
      DependencyGraph.LevelOrderQueueItem item = queue.poll();
      DependencyNode dependencyNode = item.dependencyNode;
  
      DependencyPath parentPath = item.parentPath;
      Artifact artifact = dependencyNode.getArtifact();
      if (artifact != null && parentPath != null) {
        // When requesting dependencies of 2 or more artifacts, root DependencyNode's artifact is
        // set to null
  
        // When there's an ancestor dependency node with the same groupId and artifactId as
        // the dependency, Maven will not pick up the dependency. For example, if there's a
        // dependency path "g1:a1:2.0 / ... / g1:a1:1.0" (the leftmost node as root), then Maven's
        // dependency mediation always picks g1:a1:2.0 over g1:a1:1.0.
        
        // TODO This comment doesn't seem right. That's true for the root,
        // but not for non-root nodes. A node elsewhere in the tree could cause the 
        // descendant to be selected. 
        
        String groupIdAndArtifactId = Artifacts.makeKey(artifact);
        boolean ancestorHasSameKey =
            parentPath.getArtifacts().stream()
                .map(Artifacts::makeKey)
                .anyMatch(key -> key.equals(groupIdAndArtifactId));
        if (ancestorHasSameKey) {
          continue;
        }
      }
  
      // parentPath is null for the first item
      DependencyPath path =
          parentPath == null
              ? new DependencyPath(artifact)
              : parentPath.append(dependencyNode.getDependency());
      graph.addPath(path);
  
      for (DependencyNode child : dependencyNode.getChildren()) {
        queue.add(new DependencyGraph.LevelOrderQueueItem(child, path));
      }
    }
  
    return graph;
  }
  
}
