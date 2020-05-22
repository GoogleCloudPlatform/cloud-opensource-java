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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor;

/**
 * This class builds dependency graphs for Maven artifacts.
 *
 * <p>A Maven dependency graph is the tree you see in {@code mvn dependency:tree} output. This graph
 * has the following attributes:
 *
 * <ul>
 *   <li>It contains at most one node with the same group ID and artifact ID. (<a
 *       href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Transitive_Dependencies">dependency
 *       mediation</a>)
 *   <li>The scope of a dependency affects the scope of its children's dependencies as per <a
 *       href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">Maven:
 *       Dependency Scope</a>
 *   <li>It does not contain provided-scope dependencies of transitive dependencies.
 *   <li>It does not contain optional dependencies of transitive dependencies.
 * </ul>
 *
 * <p>A full dependency graph is a dependency tree where each node's dependencies are resolved
 * recursively. This graph has the following attributes:
 *
 * <ul>
 *   <li>The same artifact, which has the same group:artifact:version, appears in different nodes in
 *       the graph.
 *   <li>The scope of a dependency does not affect the scope of its children's dependencies.
 *   <li>Provided-scope and optional dependencies are not treated differently than any other
 *       dependency.
 * </ul>
 */
public final class DependencyGraphBuilder {

  private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();

  /** Maven repositories to use when resolving dependencies. */
  private final ImmutableList<RemoteRepository> repositories;
  private Path localRepository;

  static {
    OsProperties.detectOsProperties().forEach(System::setProperty);
  }

  public DependencyGraphBuilder() {
    this(ImmutableList.of(CENTRAL.getUrl()));
  }

  /**
   * @param mavenRepositoryUrls remote Maven repositories to search for dependencies
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
  
  /**
   * Enable temporary repositories for tests.
   */
  @VisibleForTesting
  void setLocalRepository(Path localRepository) {
    this.localRepository = localRepository;
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

    DefaultRepositorySystemSession session =
        fullDependencies
            ? RepositoryUtility.newSessionForFullDependency(system)
            : RepositoryUtility.newSession(system);
            
    if (localRepository != null) {
      LocalRepository local = new LocalRepository(localRepository.toAbsolutePath().toString());
      session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, local));
    }

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
    return dependencyResult.getRoot();
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates, conflicting
   * versions, and provided and optional dependencies. In the event of I/O errors, missing
   * artifacts, and other problems, it can return an incomplete graph.
   *
   * @param artifacts Maven artifacts to retrieve their dependencies
   * @return dependency graph representing the tree of Maven artifacts
   */
  public DependencyGraphResult buildFullDependencyGraph(List<Artifact> artifacts) {
    ImmutableList<DependencyNode> dependencyNodes =
        artifacts.stream().map(DefaultDependencyNode::new).collect(toImmutableList());
    return buildDependencyGraph(dependencyNodes, GraphTraversalOption.FULL);
  }

  /**
   * Builds the transitive dependency graph as seen by Maven. It does not include duplicates and
   * conflicting versions. That is, this resolves conflicting versions by picking the first version
   * seen. This is how Maven normally operates.
   * 
   * In the event of I/O errors, missing artifacts, and other problems, it can
   * return an incomplete graph.
   */
  public DependencyGraphResult buildMavenDependencyGraph(Dependency dependency) {
    return buildDependencyGraph(
        ImmutableList.of(new DefaultDependencyNode(dependency)), GraphTraversalOption.MAVEN);
  }

  private DependencyGraphResult buildDependencyGraph(
      List<DependencyNode> dependencyNodes, GraphTraversalOption traversalOption) {
    boolean fullDependency = traversalOption == GraphTraversalOption.FULL;
    DependencyNode node;
    ImmutableSet.Builder<UnresolvableArtifactProblem> artifactProblems = ImmutableSet.builder();

    try {
      node = resolveCompileTimeDependencies(dependencyNodes, fullDependency);
    } catch (DependencyResolutionException ex) {
      DependencyResult result = ex.getResult();
      node = result.getRoot();

      Set<Artifact> checkedArtifact = new HashSet<>();
      for (ArtifactResult artifactResult : result.getArtifactResults()) {
        Artifact resolvedArtifact = artifactResult.getArtifact();
        if (resolvedArtifact == null) {
          Artifact requestedArtifact = artifactResult.getRequest().getArtifact();
          artifactProblems.add(createUnresolvableArtifactProblem(node, requestedArtifact));
          if (checkedArtifact.add(requestedArtifact)) {
          }
        }
      }
    }

    DependencyGraph graph = levelOrder(node);
    return new DependencyGraphResult(graph, artifactProblems.build());
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

    // Null for the first item
    final DependencyPath parentPath;

    LevelOrderQueueItem(DependencyNode dependencyNode, DependencyPath parentPath) {
      this.dependencyNode = dependencyNode;
      this.parentPath = parentPath;
    }
  }

  private enum GraphTraversalOption {
    /** Normal Maven dependency graph */
    MAVEN,

    /** The full dependency graph */
    FULL;
  }

  /**
   * Builds a dependency graph by traversing dependency tree in level-order (breadth-first search).
   *
   * <p>When {@code graphTraversalOption} is FULL_DEPENDENCY or FULL_DEPENDENCY_WITH_PROVIDED, then
   * it resolves the dependency of the artifact of each node in the dependency tree; otherwise it
   * just follows the given dependency tree starting with firstNode.
   *
   * @param firstNode node to start traversal
   */
  public static DependencyGraph levelOrder(DependencyNode firstNode) {

    DependencyGraph graph = new DependencyGraph();

    Queue<LevelOrderQueueItem> queue = new ArrayDeque<>();
    queue.add(new LevelOrderQueueItem(firstNode, null));

    while (!queue.isEmpty()) {
      LevelOrderQueueItem item = queue.poll();
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
        queue.add(new LevelOrderQueueItem(child, path));
      }
    }

    return graph;
  }

}
