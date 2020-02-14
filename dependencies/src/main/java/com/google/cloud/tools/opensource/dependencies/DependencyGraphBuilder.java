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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor;

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
  private final Map<Dependency, DependencyNode> cacheForFullDependency = new HashMap<>();

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

  private DependencyNode resolveCompileTimeDependencies(
      List<DependencyNode> dependencyNodes, boolean fullDependencies)
      throws DependencyResolutionException {

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
    Map<Dependency, DependencyNode> cache = fullDependencies ? cacheForFullDependency : null;
    // cacheKey is null when there's no need to use cache. Cache is only needed for a single
    // artifact's dependency resolution. A call with multiple dependencyNodes will not come again
    // in our usage.
    Dependency cacheKey =
        (cache != null && dependencyList.size() == 1) ? dependencyList.get(0) : null;
    if (cacheKey != null && cache.containsKey(cacheKey)) {
      return cache.get(cacheKey);
    }

    RepositorySystemSession session =
        fullDependencies
            ? RepositoryUtility.newSessionForFullDependency(system)
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

  /**
   * Finds the full compile time, transitive dependency graph including duplicates, conflicting
   * versions, and dependencies with 'provided' scope.
   *
   * @param artifacts Maven artifacts to retrieve their dependencies
   * @return dependency graph representing the tree of Maven artifacts
   */
  public DependencyGraphResult buildFullDependencyGraph(List<Artifact> artifacts) {
    ImmutableList<DependencyNode> dependencyNodes =
        artifacts.stream().map(DefaultDependencyNode::new).collect(toImmutableList());
    return buildDependencyGraph(dependencyNodes, GraphTraversalOption.FULL_DEPENDENCY);
  }

  /**
   * Finds the complete transitive dependency graph as seen by Maven. It does not include duplicates
   * and conflicting versions. That is, this resolves conflicting versions by picking the first
   * version seen. This is how Maven normally operates.
   */
  public DependencyGraphResult buildMavenDependencyGraph(Dependency dependency) {
    return buildDependencyGraph(
        ImmutableList.of(new DefaultDependencyNode(dependency)), GraphTraversalOption.NONE);
  }

  private DependencyGraphResult buildDependencyGraph(
      List<DependencyNode> dependencyNodes, GraphTraversalOption traversalOption) {
    boolean fullDependency = traversalOption == GraphTraversalOption.FULL_DEPENDENCY;
    DependencyNode node;
    ImmutableSet.Builder<UnresolvableArtifactProblem> artifactProblems = ImmutableSet.builder();

    try {
      node = resolveCompileTimeDependencies(dependencyNodes, fullDependency);
    } catch (DependencyResolutionException ex) {
      DependencyResult result = ex.getResult();
      node = result.getRoot();
      for (ArtifactResult artifactResult : result.getArtifactResults()) {
        Artifact resolvedArtifact = artifactResult.getArtifact();
        if (resolvedArtifact != null) {
          continue;
        }
        Artifact requestedArtifact = artifactResult.getRequest().getArtifact();
        artifactProblems.add(createUnresolvableArtifactProblem(node, requestedArtifact));
      }
    }

    DependencyGraphResult result = levelOrder(node, traversalOption);
    // Duplicate problems found in resolveDependencyGraph and levelOrder are removed by ImmutableSet
    artifactProblems.addAll(result.getArtifactProblems());

    return new DependencyGraphResult(result.getDependencyGraph(), artifactProblems.build());
  }

  /**
   * Returns a problem describing that {@code artifact} is unresolvable in the {@code
   * dependencyGraph}.
   */
  public static UnresolvableArtifactProblem createUnresolvableArtifactProblem(
      DependencyNode dependencyGraph, Artifact artifact) {
    ImmutableList<List<DependencyNode>> paths = findArtifactPaths(dependencyGraph, artifact);
    if (paths.isEmpty()) {
      // On certain conditions, Maven throws ArtifactDescriptorException even when the
      // (transformed) dependency dependencyGraph does not contain the problematic artifact any
      // more.
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
    PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor(filter);
    root.accept(visitor);
    return ImmutableList.copyOf(visitor.getPaths());
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
    /**
     * Option for the default dependency graph by Maven. This dependency graph has the following
     * attributes:
     *
     * <ul>
     *   <li>It contains at most one node for the same groupId and artifactId.
     *   <li>It does not contain transitive provided-scope dependencies
     *   <li>It does not contain transitive optional dependencies
     * </ul>
     */
    NONE,

    /**
     * Option for the full dependency graph. This dependency graph has the following attributes:
     *
     * <ul>
     *   <li>It may contain different dependency nodes for the same groupId and artifactId.
     *   <li>It may contain transitive provided-scope dependencies
     *   <li>It may contain transitive optional dependencies
     * </ul>
     */
    FULL_DEPENDENCY;
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

    boolean resolveFullDependency = graphTraversalOption == GraphTraversalOption.FULL_DEPENDENCY;
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
            dependencyNode =
                resolveCompileTimeDependencies(
                    ImmutableList.of(dependencyNode), resolveFullDependency);
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

    DependencyNode lastParent = Iterables.getLast(parentNodes);

    // Duplicate happens when root artifact is unavailable. For example:
    // xerces:xerces-impl:jar:2.6.2 was not resolved. Dependency path: ant:ant:jar:1.6.2 (compile)
    //   > xerces:xerces-impl:jar:2.6.2 (compile?) > xerces:xerces-impl:jar:2.6.2 (compile?)
    if (!lastParent.getDependency().equals(failedDependencyNode.getDependency())) {
      // Add child only when it's not duplicate
      fullPath.add(failedDependencyNode);
    }
    return fullPath;
  }
}
