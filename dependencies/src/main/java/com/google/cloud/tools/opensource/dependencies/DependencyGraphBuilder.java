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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import java.util.stream.Collectors;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;

/**
 * Based on the <a href="https://maven.apache.org/resolver/index.html">Apache Maven Artifact
 * Resolver</a> (formerly known as Eclipse Aether).
 */
public class DependencyGraphBuilder {
  
  private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();
  
  static {
    // os.detected.classifier system property used to select Netty deps
    String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
    if ((OS.indexOf("mac") >= 0) || (OS.indexOf("darwin") >= 0)) {
      System.setProperty("os.detected.classifier", "osx-x86_64");
    } else if (OS.indexOf("win") >= 0) {
      System.setProperty("os.detected.classifier", "windows-x86_64");
    } else if (OS.indexOf("nux") >= 0) {
      System.setProperty("os.detected.classifier", "linux-x86_64");
    } else {
      // Since we only load the dependency graph, not actually use the 
      // dependency, it doesn't matter a great deal which one we pick.
      System.setProperty("os.detected.classifier", "linux-x86_64");      
    }
  }

  // caching cuts time by about a factor of 4.
  private static final Map<String, DependencyNode> cache = new HashMap<>();

  @VisibleForTesting
  static DependencyNode resolveCompileTimeRootDependencies(Artifact artifact)
      throws DependencyCollectionException, DependencyResolutionException {
    
    String key = Artifacts.toCoordinates(artifact);
    if (cache.containsKey(key)) {
      return cache.get(key);
    }
    
    RepositorySystemSession session = RepositoryUtility.newSession(system);

    Dependency dependency = new Dependency(artifact, "compile");

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(dependency);
    collectRequest.addRepository(RepositoryUtility.CENTRAL);
    DependencyNode node = system.collectDependencies(session, collectRequest).getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(node);
    // TODO might be able to speed up by using collectDependencies here instead
    system.resolveDependencies(session, dependencyRequest);
    cache.put(key, node);
    
    return node;
  }

  private static DependencyNode resolveCompileTimeDependencies(List<Artifact> artifacts)
      throws DependencyCollectionException, DependencyResolutionException {
    String key = artifacts.stream().map(Artifacts::toCoordinates).collect(Collectors.joining(","));
    if (cache.containsKey(key)) {
      return cache.get(key);
    }

    List<Dependency> dependencyList =
        artifacts
            .stream()
            .map(artifact -> new Dependency(artifact, "compile"))
            .collect(Collectors.toList());

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setDependencies(dependencyList);
    collectRequest.addRepository(RepositoryUtility.CENTRAL);
    RepositorySystemSession session = RepositoryUtility.newSession(system);
    DependencySelector dependencySelector = session.getDependencySelector();
    // Can we modify the behavior of OptionalDependencySelector behind andDependencySelector?
    AndDependencySelector andDependencySelector = (AndDependencySelector) dependencySelector;
    CollectResult collectResult = system.collectDependencies(session, collectRequest);
    // This root DependencyNode's artifact is set to null, as root dependency was null in request
    DependencyNode node = collectResult.getRoot();
    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setCollectRequest(collectRequest);

    system.resolveDependencies(session, dependencyRequest);
    cache.put(key, node);

    return node;
  }

  /**
   * Returns the non-transitive compile time dependencies of an artifact.
   */
  public static List<Artifact> getDirectDependencies(Artifact artifact)
      throws DependencyCollectionException, DependencyResolutionException {
    
    List<Artifact> result = new ArrayList<>();
    
    DependencyNode node = resolveCompileTimeRootDependencies(artifact);
    for (DependencyNode child : node.getChildren()) {
      result.add(child.getArtifact());
    }
    return result;
  }
  
  /**
   * Finds the full compile time, transitive dependency graph including duplicates
   * and conflicting versions.
   */
  public static DependencyGraph getCompleteDependencies(Artifact artifact)
      throws DependencyCollectionException, DependencyResolutionException {
    
    // root node
    DependencyNode node = resolveCompileTimeRootDependencies(artifact);
    DependencyGraph graph = new DependencyGraph();
    levelOrder(node, graph, true);
    
    return graph;
  }
  
  /**
   * Finds the complete transitive dependency graph as seen by Maven.
   * It does not include duplicates and conflicting versions. That is,
   * this resolves conflicting versions by picking the first version
   * seen. This is how Maven normally operates.
   */
  public static DependencyGraph getTransitiveDependencies(Artifact artifact)
      throws DependencyCollectionException, DependencyResolutionException {
    // root node
    DependencyNode node = resolveCompileTimeRootDependencies(artifact);
    DependencyGraph graph = new DependencyGraph();
    levelOrder(node, graph);
    return graph;
  }

  static DependencyGraph getTransitiveDependencies(List<Artifact> artifacts)
      throws DependencyCollectionException, DependencyResolutionException {
    // root node artifact is null and the node has dependencies as children
    DependencyNode node = resolveCompileTimeDependencies(artifacts);
    DependencyGraph graph = new DependencyGraph();
    levelOrder(node, graph);
    return graph;
  }

  private static final class LevelOrderQueueItem {
    final DependencyNode dependencyNode;
    final Stack<DependencyNode> parentNodes;

    LevelOrderQueueItem(DependencyNode dependencyNode,
        Stack<DependencyNode> parentNodes) {
      this.dependencyNode = dependencyNode;
      this.parentNodes = parentNodes;
    }
  }

  private static void levelOrder(DependencyNode node, DependencyGraph graph) {
    try {
      levelOrder(node, graph, false);
    } catch (RepositoryException ex) {
      throw new RuntimeException(
          "There was problem in resolving dependencies even when it is not supposed to resolve dependency",
          ex);
    }
  }

  /**
   * Traverses dependency tree in level-order (breadth-first search) and stores {@link
   * DependencyPath} instances corresponding to tree nodes to {@link DependencyGraph}. When
   * resolveFullDependency flag is true, then it resolves the dependency of the artifact of the each
   * node in the dependency tree; otherwise it just follows the given dependency tree starting with
   * firstNode.
   *
   * @param firstNode node to start traversal
   * @param graph graph to store {@link DependencyPath} instances
   * @param resolveFullDependency flag to resolve dependency for each node in the tree. Useful for
   *     building a complete tree of dependencies including <i>provided</i> scope
   * @throws DependencyCollectionException when there is a problem in collecting dependency. This
   *     happens only when resolveFullDependency is true.
   * @throws DependencyResolutionException when there is a problem in resolving dependency. This
   *     happens only when resolveFullDependency is true.
   */
  private static void levelOrder(
      DependencyNode firstNode, DependencyGraph graph, boolean resolveFullDependency)
      throws DependencyCollectionException, DependencyResolutionException {
    Queue<LevelOrderQueueItem> queue = new ArrayDeque<>();
    queue.add(new LevelOrderQueueItem(firstNode, new Stack<>()));
    while (!queue.isEmpty()) {
      LevelOrderQueueItem item = queue.poll();
      DependencyNode dependencyNode = item.dependencyNode;
      DependencyPath forPath = new DependencyPath();
      Stack<DependencyNode> parentNodes = item.parentNodes;
      parentNodes.forEach(parentNode -> forPath.add(parentNode.getArtifact()));
      if (dependencyNode.getArtifact() != null) {
        // When requesting dependencies of 2 or more artifacts, root DependencyNode's artifact is
        // set to null
        forPath.add(dependencyNode.getArtifact());
        if (resolveFullDependency && parentNodes.contains(dependencyNode)) {
          // TODO: change to logger
          System.err.println("Infinite recursion resolving " + dependencyNode);
          System.err.println("Likely cycle in " + parentNodes);
          System.err.println("Child " + dependencyNode);
          return;
        }
        parentNodes.push(dependencyNode);
        graph.addPath(forPath);

        if (resolveFullDependency && !"system".equals(dependencyNode.getDependency().getScope())) {
          try {
            dependencyNode = resolveCompileTimeRootDependencies(dependencyNode.getArtifact());
          } catch (DependencyResolutionException ex) {
            // TODO: change to logger
            System.err.println("Error resolving " + dependencyNode + " under " + parentNodes);
            System.err.println(ex.getMessage());
            throw ex;
          }
        }
      }
      for (DependencyNode child : dependencyNode.getChildren()) {
        queue.add(new LevelOrderQueueItem(child, (Stack<DependencyNode>) parentNodes.clone()));
      }
    }
  }
}
