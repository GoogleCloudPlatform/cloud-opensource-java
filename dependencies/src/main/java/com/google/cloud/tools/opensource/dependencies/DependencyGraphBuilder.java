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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Based on the <a href="https://maven.apache.org/resolver/index.html">Apache Maven Artifact
 * Resolver</a> (formerly known as Eclipse Aether).
 */
public class DependencyGraphBuilder {

  private static final Logger logger = Logger.getLogger(DependencyGraphBuilder.class.getName());
  
  private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();
  
  static {
    setDetectedOsSystemProperties();
  }

  // caching cuts time by about a factor of 4.
  private static final Map<String, DependencyNode> cacheWithProvidedScope = new HashMap<>();
  private static final Map<String, DependencyNode> cacheWithoutProvidedScope = new HashMap<>();

  private static void setDetectedOsSystemProperties() {
    // System properties to select Netty dependencies through os-maven-plugin
    // Definition of the properties: https://github.com/trustin/os-maven-plugin
    String nonAlphaNumericPattern = "[^a-z0-9]+";
    String osNameNormalized =
        System.getProperty("os.name", "generic")
            .toLowerCase(Locale.ENGLISH)
            .replaceAll(nonAlphaNumericPattern, "");

    String osDetectedNameKey = "os.detected.name";
    String osDetectedNameValue;
    if (osNameNormalized.startsWith("macosx") || osNameNormalized.startsWith("osx")) {
      osDetectedNameValue = "osx";
    } else if (osNameNormalized.startsWith("windows")) {
      osDetectedNameValue = "windows";
    } else {
      // Since we only load the dependency graph, not actually use the
      // dependency, it doesn't matter a great deal which one we pick.
      osDetectedNameValue = "linux";
    }
    System.setProperty(osDetectedNameKey, osDetectedNameValue);

    String osDetectedArchKey = "os.detected.arch";
    String osDetectedArchValue;
    String osArchNormalized = System.getProperty("os.arch").replaceAll(nonAlphaNumericPattern, "");
    if (ImmutableList.of("x8664", "amd64", "ia32e", "em64t", "x64").contains(osArchNormalized)) {
      osDetectedArchValue = "x86_64";
    } else {
      osDetectedArchValue = "x86_32";
    }
    System.setProperty(osDetectedArchKey, osDetectedArchValue);

    System.setProperty("os.detected.classifier", osDetectedNameValue + "-" + osDetectedArchValue);
  }

  private static DependencyNode resolveCompileTimeDependencies(Artifact rootDependencyArtifact)
      throws DependencyCollectionException, DependencyResolutionException {
    return resolveCompileTimeDependencies(rootDependencyArtifact, false);
  }

  private static DependencyNode resolveCompileTimeDependencies(
      Artifact rootDependencyArtifact, boolean includeProvidedScope)
      throws DependencyCollectionException, DependencyResolutionException {
    return resolveCompileTimeDependencies(
        ImmutableList.of(rootDependencyArtifact), includeProvidedScope);
  }

  private static DependencyNode resolveCompileTimeDependencies(
      List<Artifact> dependencyArtifacts, boolean includeProvidedScope)
      throws DependencyCollectionException, DependencyResolutionException {

    Map<String, DependencyNode> cache =
        includeProvidedScope ? cacheWithProvidedScope : cacheWithoutProvidedScope;
    String cacheKey =
        dependencyArtifacts.stream().map(Artifacts::toCoordinates).collect(Collectors.joining(","));
    if (cache.containsKey(cacheKey)) {
      return cache.get(cacheKey);
    }

    RepositorySystemSession session =
        includeProvidedScope
            ? RepositoryUtility.newSessionWithProvidedScope(system)
            : RepositoryUtility.newSession(system);

    CollectRequest collectRequest = new CollectRequest();

    ImmutableList<Dependency> dependencyList =
        dependencyArtifacts
            .stream()
            .map(artifact -> new Dependency(artifact, "compile"))
            .collect(toImmutableList());
    if (dependencyList.size() == 1) {
      // With setRoot, the result includes dependencies with `optional:true` or `provided`
      collectRequest.setRoot(dependencyList.get(0));
    } else {
      collectRequest.setDependencies(dependencyList);
    }
    collectRequest.addRepository(RepositoryUtility.CENTRAL);
    CollectResult collectResult = system.collectDependencies(session, collectRequest);
    DependencyNode node = collectResult.getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(node);
    dependencyRequest.setCollectRequest(collectRequest);

    // This might be able to speed up by using collectDependencies here instead
    system.resolveDependencies(session, dependencyRequest);

    cache.put(cacheKey, node);

    return node;
  }

  /**
   * Returns the non-transitive compile time dependencies of an artifact.
   */
  public static List<Artifact> getDirectDependencies(Artifact artifact)
      throws DependencyCollectionException, DependencyResolutionException {
    
    List<Artifact> result = new ArrayList<>();
    
    DependencyNode node = resolveCompileTimeDependencies(artifact);
    for (DependencyNode child : node.getChildren()) {
      result.add(child.getArtifact());
    }
    return result;
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates,
   * conflicting versions, and dependencies with 'provided' scope.
   *
   * @param artifacts Maven artifacts to retrieve their dependencies
   * @return dependency graph representing the tree of Maven artifacts
   * @throws DependencyCollectionException when there is a problem in collecting dependency
   * @throws DependencyResolutionException when there is a problem in resolving dependency
   */
  public static DependencyGraph getStaticLinkageCheckDependencyGraph(List<Artifact> artifacts)
      throws DependencyCollectionException, DependencyResolutionException {
    DependencyNode node = resolveCompileTimeDependencies(artifacts, true);
    DependencyGraph graph = new DependencyGraph();
    levelOrder(node, graph, GraphTraversalOption.FULL_DEPENDENCY_WITH_PROVIDED);

    return graph;
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates
   * and conflicting versions.
   */
  public static DependencyGraph getCompleteDependencies(Artifact artifact)
      throws DependencyCollectionException, DependencyResolutionException {
    
    // root node
    DependencyNode node = resolveCompileTimeDependencies(artifact);
    DependencyGraph graph = new DependencyGraph();
    levelOrder(node, graph, GraphTraversalOption.FULL_DEPENDENCY);
    
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
    DependencyNode node = resolveCompileTimeDependencies(artifact);
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
      levelOrder(node, graph, GraphTraversalOption.NONE);
    } catch (RepositoryException ex) {
      throw new RuntimeException(
          "Problem resolving dependencies even though it is not supposed to resolve dependency",
          ex);
    }
  }

  private enum GraphTraversalOption {
    NONE,
    FULL_DEPENDENCY,
    FULL_DEPENDENCY_WITH_PROVIDED;

    private boolean resolveFullDependencies() {
      return this == FULL_DEPENDENCY
          || this == FULL_DEPENDENCY_WITH_PROVIDED;
    }
  }

  /**
   * Traverses dependency tree in level-order (breadth-first search) and stores {@link
   * DependencyPath} instances corresponding to tree nodes to {@link DependencyGraph}. When
   * {@code graphTraversalOption} is FULL_DEPENDENCY or FULL_DEPENDENCY_WITH_PROVIDED,
   * then it resolves the dependency of the artifact of the each
   * node in the dependency tree; otherwise it just follows the given dependency tree starting with
   * firstNode.
   *
   * @param firstNode node to start traversal
   * @param graph graph to store {@link DependencyPath} instances
   * @param graphTraversalOption option to recursively resolve the dependency to build complete
   *     dependency tree, with or without dependencies of provided scope
   * @throws DependencyCollectionException when there is a problem in collecting dependency. This
   *     happens only when graphTraversalOption is FULL_DEPENDENCY or FULL_DEPENDENCY_WITH_PROVIDED.
   * @throws DependencyResolutionException when there is a problem in resolving dependency. This
   *     happens only when graphTraversalOption is FULL_DEPENDENCY or FULL_DEPENDENCY_WITH_PROVIDED.
   */
  private static void levelOrder(
      DependencyNode firstNode, DependencyGraph graph, GraphTraversalOption graphTraversalOption)
      throws DependencyCollectionException, DependencyResolutionException {

    boolean resolveFullDependency = graphTraversalOption.resolveFullDependencies();
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
          logger.severe(
              "Infinite recursion resolving "
                  + dependencyNode
                  + ". Likely cycle in "
                  + parentNodes);
          continue;
        }
        parentNodes.push(dependencyNode);
        graph.addPath(forPath);

        if (resolveFullDependency && !"system".equals(dependencyNode.getDependency().getScope())) {
          try {
            boolean includeProvidedScope =
                graphTraversalOption == GraphTraversalOption.FULL_DEPENDENCY_WITH_PROVIDED;
            dependencyNode =
                resolveCompileTimeDependencies(dependencyNode.getArtifact(), includeProvidedScope);
          } catch (DependencyResolutionException ex) {
            // A dependency may be unavailable. For example, com.google.guava:guava-gwt:jar:20.0
            // has a transitive dependency to org.eclipse.jdt.core.compiler:ecj:jar:4.4RC4 (not
            // found in Maven central)
            logger.warning(
                "Could not resolve "
                    + dependencyNode
                    + " under "
                    + parentNodes // This shows "(compile?)" if optional:true
                    + " Exception: "
                    + ex.getMessage());
            boolean hasOptionalParent =
                parentNodes.stream().anyMatch(node -> node.getDependency().isOptional());
            boolean hasProvidedParent =
                parentNodes
                    .stream()
                    .anyMatch(node -> "provided".equals(node.getDependency().getScope()));
            if (hasOptionalParent && hasProvidedParent) {
              logger.warning(
                  "Skipping this dependency as it has both optional:true and scope:provided");
            } else {
              throw ex;
            }
          }
        }
      }
      for (DependencyNode child : dependencyNode.getChildren()) {
        @SuppressWarnings("unchecked")
        Stack<DependencyNode> clone = (Stack<DependencyNode>) parentNodes.clone();
        queue.add(new LevelOrderQueueItem(child, clone));
      }
    }
  }
}
