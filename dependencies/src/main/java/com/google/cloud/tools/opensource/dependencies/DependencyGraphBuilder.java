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

import static com.google.cloud.tools.opensource.dependencies.RepositoryUtility.CENTRAL;
import static com.google.cloud.tools.opensource.dependencies.RepositoryUtility.mavenRepositoryFromUrl;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

/**
 * Based on the <a href="https://maven.apache.org/resolver/index.html">Apache Maven Artifact
 * Resolver</a> (formerly known as Eclipse Aether).
 */
public final class DependencyGraphBuilder {

  private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();

  private static final CharMatcher LOWER_ALPHA_NUMERIC =
      CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9'));

  /** Maven Repositories to use when resolving dependencies. */
  private final ImmutableList<RemoteRepository> repositories;

  static {
    detectOsProperties().forEach(System::setProperty);
  }

  // caching cuts time by about a factor of 4. Dependency class's equality includes exclusions.
  private final Map<Dependency, DependencyNode> cacheWithProvidedScope = new HashMap<>();
  private final Map<Dependency, DependencyNode> cacheWithoutProvidedScope = new HashMap<>();

  public static ImmutableMap<String, String> detectOsProperties() {
    // System properties to select Netty dependencies through os-maven-plugin
    // Definition of the properties: https://github.com/trustin/os-maven-plugin
    String osDetectedName = osDetectedName();
    String osDetectedArch = osDetectedArch();
    return ImmutableMap.of(
        "os.detected.name",
        osDetectedName,
        "os.detected.arch",
        osDetectedArch,
        "os.detected.classifier",
        osDetectedName + "-" + osDetectedArch);
  }

  private static String osDetectedName() {
    String osNameNormalized =
        LOWER_ALPHA_NUMERIC.retainFrom(System.getProperty("os.name").toLowerCase(Locale.ENGLISH));

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
        LOWER_ALPHA_NUMERIC.retainFrom(System.getProperty("os.arch").toLowerCase(Locale.ENGLISH));
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

  public DependencyGraphBuilder() {
    this(ImmutableList.of(CENTRAL.getUrl()));
  }

  /**
   * @param mavenRepositoryUrls Maven repository URLs to search for dependencies
   * @throws IllegalArgumentException if a URL is malformed or does not have an allowed scheme
   */
  public DependencyGraphBuilder(Iterable<String> mavenRepositoryUrls) {
    ImmutableList.Builder<RemoteRepository> repositoryListBuilder = ImmutableList.builder();
    for (String mavenRepositoryUrl : mavenRepositoryUrls) {
      RemoteRepository repository = mavenRepositoryFromUrl(mavenRepositoryUrl);
      repositoryListBuilder.add(repository);
    }
    this.repositories = repositoryListBuilder.build();
  }

  private DependencyNode resolveDependencyGraph(
      List<DependencyNode> dependencyNodes, boolean includeProvidedScope)
      throws DependencyCollectionException, DependencyResolutionException {

    ImmutableList.Builder<Dependency> dependenciesBuilder = ImmutableList.builder();
    for (DependencyNode dependencyNode : dependencyNodes) {
      Dependency dependency = dependencyNode.getDependency();
      if (dependency == null) {
        // Root DependencyNode has null dependency field.
        dependenciesBuilder.add(new Dependency(dependencyNode.getArtifact(), "compile"));
      } else {
        // The dependency field carries exclusions
        dependenciesBuilder.add(dependency.setScope("compile"));
      }
    }
    ImmutableList<Dependency> dependencyList = dependenciesBuilder.build();

    // The cache key includes exclusion elements of Maven artifacts
    Map<Dependency, DependencyNode> cache =
        includeProvidedScope ? cacheWithProvidedScope : cacheWithoutProvidedScope;
    // cacheKey is null when there's no need to use cache. Cache is only needed for a single
    // artifact's dependency resolution. A call with multiple dependencyNodes will not come again
    // in our usage.
    Dependency cacheKey = dependencyList.size() == 1 ? dependencyList.get(0) : null;
    if (cacheKey != null && cache.containsKey(cacheKey)) {
      return cache.get(cacheKey);
    }

    RepositorySystemSession session =
        includeProvidedScope
            ? RepositoryUtility.newSessionWithProvidedScope(system)
            : RepositoryUtility.newSession(system);

    CollectRequest collectRequest = new CollectRequest();
    if (dependencyList.size() == 1) {
      // With setRoot, the result includes dependencies with `optional:true` or `provided`
      collectRequest.setRoot(dependencyList.get(0));
    } else {
      collectRequest.setDependencies(dependencyList);
    }
    for (RemoteRepository repository : repositories) {
      collectRequest.addRepository(repository);
    }
    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setCollectRequest(collectRequest);

    // resolveDependencies equals to calling both collectDependencies (build dependency tree) and
    // resolveArtifacts (download JAR files).
    DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
    DependencyNode node = dependencyResult.getRoot();

    if (cacheKey != null) {
      cache.put(cacheKey, node);
    }

    return node;
  }

  /** Returns the non-transitive compile time dependencies of a dependency. */
  List<DependencyNode> getDirectDependencies(Dependency dependency) throws RepositoryException {

    List<DependencyNode> result = new ArrayList<>();

    DependencyNode node =
        resolveDependencyGraph(ImmutableList.of(new DefaultDependencyNode(dependency)), false);
    for (DependencyNode child : node.getChildren()) {
      result.add(child);
    }
    return result;
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates, conflicting
   * versions, and dependencies with 'provided' scope.
   *
   * @param artifacts Maven artifacts to retrieve their dependencies
   * @return dependency graph representing the tree of Maven artifacts
   * @throws RepositoryException when there is a problem resolving or collecting dependencies
   */
  public DependencyGraphResult buildLinkageCheckDependencyGraph(List<Artifact> artifacts)
      throws RepositoryException {
    ImmutableList<DependencyNode> dependencyNodes =
        artifacts.stream().map(DefaultDependencyNode::new).collect(toImmutableList());
    DependencyNode node = resolveDependencyGraph(dependencyNodes, true);
    return levelOrder(node, GraphTraversalOption.FULL_DEPENDENCY_WITH_PROVIDED);
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates and conflicting
   * versions.
   */
  public DependencyGraphResult buildCompleteGraph(Dependency dependency)
      throws RepositoryException {

    DefaultDependencyNode root = new DefaultDependencyNode(dependency);
    DependencyNode node = resolveDependencyGraph(ImmutableList.of(root), false);
    return levelOrder(node, GraphTraversalOption.FULL_DEPENDENCY);
  }

  /**
   * Finds the complete transitive dependency graph as seen by Maven. It does not include duplicates
   * and conflicting versions. That is, this resolves conflicting versions by picking the first
   * version seen. This is how Maven normally operates.
   */
  public DependencyGraphResult buildGraph(Dependency dependency)
      throws RepositoryException {
    // root node
    DependencyNode node =
        resolveDependencyGraph(ImmutableList.of(new DefaultDependencyNode(dependency)), false);
    return levelOrder(node, GraphTraversalOption.NONE);
  }

  private static final class LevelOrderQueueItem {
    final DependencyNode dependencyNode;
    final Stack<DependencyNode> parentNodes;

    LevelOrderQueueItem(DependencyNode dependencyNode, Stack<DependencyNode> parentNodes) {
      this.dependencyNode = dependencyNode;
      this.parentNodes = parentNodes;
    }
  }

  private enum GraphTraversalOption {
    NONE,
    FULL_DEPENDENCY,
    FULL_DEPENDENCY_WITH_PROVIDED;

    private boolean resolveFullDependencies() {
      return this == FULL_DEPENDENCY || this == FULL_DEPENDENCY_WITH_PROVIDED;
    }
  }

  /**
   * Returns a dependency graph by traversing dependency tree in level-order (breadth-first search).
   *
   * <p>When {@code graphTraversalOption} is FULL_DEPENDENCY or FULL_DEPENDENCY_WITH_PROVIDED, then
   * it resolves the dependency of the artifact of each node in the dependency tree; otherwise it
   * just follows the given dependency tree starting with firstNode.
   *
   * @param firstNode node to start traversal
   * @param graphTraversalOption option to recursively resolve the dependency to build complete
   *     dependency tree, with or without dependencies of provided scope
   */
  private DependencyGraphResult levelOrder(
      DependencyNode firstNode, GraphTraversalOption graphTraversalOption) {

    DependencyGraph graph = new DependencyGraph();

    boolean resolveFullDependency = graphTraversalOption.resolveFullDependencies();
    Queue<LevelOrderQueueItem> queue = new ArrayDeque<>();
    queue.add(new LevelOrderQueueItem(firstNode, new Stack<>()));

    ImmutableList.Builder<UnresolvableArtifactProblem> artifactProblems = ImmutableList.builder();

    while (!queue.isEmpty()) {
      LevelOrderQueueItem item = queue.poll();
      DependencyNode dependencyNode = item.dependencyNode;
      DependencyPath path = new DependencyPath();
      Stack<DependencyNode> parentNodes = item.parentNodes;
      parentNodes.forEach(
          parentNode -> path.add(parentNode.getDependency()));
      Artifact artifact = dependencyNode.getArtifact();
      if (artifact != null) {
        // When requesting dependencies of 2 or more artifacts, root DependencyNode's artifact is
        // set to null

        // When there's a parent dependency node with the same groupId and artifactId as
        // the dependency, Maven will not pick up the dependency. For example, if there's a
        // dependency path "g1:a1:2.0 / ... / g1:a1:1.0" (the leftmost node as root), then Maven's
        // dependency mediation always picks up g1:a1:2.0 over g1:a1:1.0.
        String groupIdAndArtifactId = Artifacts.makeKey(artifact);
        boolean parentHasSameKey =
            parentNodes.stream()
                .map(node -> Artifacts.makeKey(node.getArtifact()))
                .anyMatch(key -> key.equals(groupIdAndArtifactId));
        if (parentHasSameKey) {
          continue;
        }

        path.add(dependencyNode.getDependency());
        parentNodes.push(dependencyNode);
        graph.addPath(path);

        if (resolveFullDependency && !"system".equals(dependencyNode.getDependency().getScope())) {
          try {
            boolean includeProvidedScope =
                graphTraversalOption == GraphTraversalOption.FULL_DEPENDENCY_WITH_PROVIDED;
            dependencyNode =
                resolveDependencyGraph(ImmutableList.of(dependencyNode), includeProvidedScope);
          } catch (DependencyResolutionException resolutionException) {
            // A dependency may be unavailable. For example, com.google.guava:guava-gwt:jar:20.0
            // has a transitive dependency to org.eclipse.jdt.core.compiler:ecj:jar:4.4RC4 (not
            // found in Maven central)
            for (ArtifactResult artifactResult :
                resolutionException.getResult().getArtifactResults()) {
              if (artifactResult.getArtifact() == null) {
                DependencyNode failedDependencyNode =
                    artifactResult.getRequest().getDependencyNode();
                
                List<DependencyNode> fullPath = makeFullPath(parentNodes, failedDependencyNode);
                
                artifactProblems.add(new UnresolvableArtifactProblem(fullPath));
              }
            }
          } catch (DependencyCollectionException collectionException) {
            artifactProblems.add(new UnresolvableArtifactProblem(parentNodes));
          }
        }
      }
      
      for (DependencyNode child : dependencyNode.getChildren()) {
        @SuppressWarnings("unchecked")
        Stack<DependencyNode> clone = (Stack<DependencyNode>) parentNodes.clone();
        queue.add(new LevelOrderQueueItem(child, clone));
      }
    }

    return new DependencyGraphResult(graph, artifactProblems.build());
  }

  private static List<DependencyNode> makeFullPath(
      Stack<DependencyNode> parentNodes, DependencyNode failedDependencyNode) {
    List<DependencyNode> fullPath = new ArrayList<>();
    fullPath.addAll(parentNodes);
    fullPath.add(failedDependencyNode);
    return fullPath;
  }
}
