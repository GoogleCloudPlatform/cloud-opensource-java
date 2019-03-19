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

import com.google.cloud.tools.opensource.classpath.LeagueTableMain;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
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
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Based on the <a href="https://maven.apache.org/resolver/index.html">Apache Maven Artifact
 * Resolver</a> (formerly known as Eclipse Aether).
 */
public class DependencyGraphBuilder {

  private static final Logger logger = Logger.getLogger(DependencyGraphBuilder.class.getName());

  private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();

  private static final CharMatcher LOWER_ALPHA_NUMERIC = CharMatcher.inRange('a', 'z')
      .or(CharMatcher.inRange('0', '9'));

  static {
    setDetectedOsSystemProperties();
  }

  // caching cuts time by about a factor of 4.
  private static final Map<String, DependencyNode> cacheWithProvidedScope = new HashMap<>();
  private static final Map<String, DependencyNode> cacheWithoutProvidedScope = new HashMap<>();

  private static void setDetectedOsSystemProperties() {
    // System properties to select Netty dependencies through os-maven-plugin
    // Definition of the properties: https://github.com/trustin/os-maven-plugin

    String osDetectedName = osDetectedName();
    System.setProperty("os.detected.name", osDetectedName);
    String osDetectedArch = osDetectedArch();
    System.setProperty("os.detected.arch", osDetectedArch);
    System.setProperty("os.detected.classifier", osDetectedName + "-" + osDetectedArch);
  }

  private static String osDetectedName() {
    String osNameNormalized =
        LOWER_ALPHA_NUMERIC
            .retainFrom(System.getProperty("os.name").toLowerCase(Locale.ENGLISH));

    if (osNameNormalized.startsWith("macosx") || osNameNormalized.startsWith("osx")) {
      return "osx";
    } else if (osNameNormalized.startsWith("windows")) {
      return "windows";
    }
    // Since we only load the dependency graph, not actually use the
    // dependency, it doesn't matter a great deal which one we pick.
    return "linux";
  }

  private static String osDetectedArch() {
    String osArchNormalized =
        LOWER_ALPHA_NUMERIC
            .retainFrom(System.getProperty("os.arch").toLowerCase(Locale.ENGLISH));
    switch (osArchNormalized) {
      case "x8664":
      case "amd64":
      case "ia32e":
      case "em64t":
      case "x64":
        return "x86_64";
      default:
        return "x86_32";
    }
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
    RepositoryUtility.addRepositoriesToRequest(collectRequest);

    if (!LeagueTableMain.managedDependencies.isEmpty()) {
      collectRequest.setManagedDependencies(LeagueTableMain.managedDependencies);
    }

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

  /** Returns the non-transitive compile time dependencies of an artifact. */
  public static List<Artifact> getDirectDependencies(Artifact artifact) throws RepositoryException {

    List<Artifact> result = new ArrayList<>();

    DependencyNode node = resolveCompileTimeDependencies(artifact);
    for (DependencyNode child : node.getChildren()) {
      result.add(child.getArtifact());
    }
    return result;
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates, conflicting
   * versions, and dependencies with 'provided' scope.
   *
   * @param artifacts Maven artifacts to retrieve their dependencies
   * @return dependency graph representing the tree of Maven artifacts
   * @throws RepositoryException when there is a problem in resolving or collecting dependency
   */
  public static DependencyGraph getStaticLinkageCheckDependencyGraph(List<Artifact> artifacts)
      throws RepositoryException {
    DependencyNode node = resolveCompileTimeDependencies(artifacts, true);
    DependencyGraph graph = new DependencyGraph();
    levelOrder(node, graph, GraphTraversalOption.FULL_DEPENDENCY_WITH_PROVIDED);

    return graph;
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates and conflicting
   * versions.
   */
  public static DependencyGraph getCompleteDependencies(Artifact artifact)
      throws RepositoryException {

    // root node
    DependencyNode node = resolveCompileTimeDependencies(artifact);
    DependencyGraph graph = new DependencyGraph();
    levelOrder(node, graph, GraphTraversalOption.FULL_DEPENDENCY);

    return graph;
  }

  /**
   * Finds the complete transitive dependency graph as seen by Maven. It does not include duplicates
   * and conflicting versions. That is, this resolves conflicting versions by picking the first
   * version seen. This is how Maven normally operates.
   */
  public static DependencyGraph getTransitiveDependencies(Artifact artifact)
      throws RepositoryException {
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

  private static void levelOrder(DependencyNode node, DependencyGraph graph)
      throws AggregatedRepositoryException {
    levelOrder(node, graph, GraphTraversalOption.NONE);
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
   * DependencyPath} instances corresponding to tree nodes to {@link DependencyGraph}. When {@code
   * graphTraversalOption} is FULL_DEPENDENCY or FULL_DEPENDENCY_WITH_PROVIDED, then it resolves the
   * dependency of the artifact of the each node in the dependency tree; otherwise it just follows
   * the given dependency tree starting with firstNode.
   *
   * @param firstNode node to start traversal
   * @param graph graph to store {@link DependencyPath} instances
   * @param graphTraversalOption option to recursively resolve the dependency to build complete
   *     dependency tree, with or without dependencies of provided scope
   * @throws AggregatedRepositoryException when there are one ore more problems due to {@link
   *     DependencyCollectionException} or {@link DependencyResolutionException}. This happens only
   *     when graphTraversalOption is FULL_DEPENDENCY or FULL_DEPENDENCY_WITH_PROVIDED.
   */
  private static void levelOrder(
      DependencyNode firstNode, DependencyGraph graph, GraphTraversalOption graphTraversalOption)
      throws AggregatedRepositoryException {

    boolean resolveFullDependency = graphTraversalOption.resolveFullDependencies();
    Queue<LevelOrderQueueItem> queue = new ArrayDeque<>();
    queue.add(new LevelOrderQueueItem(firstNode, new Stack<>()));

    // Records failures rather than existing immediately.
    List<ExceptionAndPath> resolutionFailures = Lists.newArrayList();

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
          Artifact dependencyNodeArtifact = dependencyNode.getArtifact();
          try {
            boolean includeProvidedScope =
                graphTraversalOption == GraphTraversalOption.FULL_DEPENDENCY_WITH_PROVIDED;
            dependencyNode =
                resolveCompileTimeDependencies(dependencyNodeArtifact, includeProvidedScope);
          } catch (DependencyResolutionException resolutionException) {
            // A dependency may be unavailable. For example, com.google.guava:guava-gwt:jar:20.0
            // has a transitive dependency to org.eclipse.jdt.core.compiler:ecj:jar:4.4RC4 (not
            // found in Maven central)
            for (ArtifactResult artifactResult :
                resolutionException.getResult().getArtifactResults()) {
              if (artifactResult.getExceptions().isEmpty()) {
                continue;
              }
              DependencyNode failedDependencyNode = artifactResult.getRequest().getDependencyNode();
              ExceptionAndPath failure =
                  ExceptionAndPath.create(parentNodes, failedDependencyNode, resolutionException);
              if (isUnacceptableResolutionException(failure)) {
                resolutionFailures.add(failure);
              }
            }
          } catch (DependencyCollectionException collectionException) {
            DependencyNode failedDependencyNode = collectionException.getResult().getRoot();
            ExceptionAndPath failure =
                ExceptionAndPath.create(parentNodes, failedDependencyNode, collectionException);
            if (isUnacceptableResolutionException(failure)) {
              resolutionFailures.add(failure);
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

    if (!resolutionFailures.isEmpty()) {
      throw new AggregatedRepositoryException(resolutionFailures);
    }
  }

  /**
   * Returns true if {@code exceptionAndPath.getPath} does not contain {@code optional} dependency
   * and the path does not contain {@code scope:provided} dependency.
   */
  private static boolean isUnacceptableResolutionException(ExceptionAndPath exceptionAndPath) {
    ImmutableList<DependencyNode> dependencyNodes = exceptionAndPath.getPath();
    boolean hasOptionalParent =
        dependencyNodes.stream().anyMatch(node -> node.getDependency().isOptional());
    if (!hasOptionalParent) {
      return true;
    }
    boolean hasProvidedParent =
        dependencyNodes.stream()
            .anyMatch(node -> "provided".equals(node.getDependency().getScope()));
    return !hasProvidedParent;
  }
}
