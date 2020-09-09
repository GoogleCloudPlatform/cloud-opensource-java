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
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

/**
 * Builds dependency graphs for Maven artifacts by querying repositories for
 * pom.xml files and following the dependency chains therein.
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
      List<DependencyNode> dependencyNodes, RepositorySystemSession session)
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

    if (localRepository != null && session instanceof DefaultRepositorySystemSession) {
      LocalRepository local = new LocalRepository(localRepository.toAbsolutePath().toString());
      ((DefaultRepositorySystemSession) session)
          .setLocalRepositoryManager(system.newLocalRepositoryManager(session, local));
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
   * versions, and provided and optional dependencies.
   *
   * <p>In the event of I/O errors, missing artifacts, and other problems, it can return an
   * incomplete graph. Each node's dependencies are resolved recursively. The scope of a dependency
   * does not affect the scope of its children's dependencies. Provided and optional dependencies
   * are not treated differently than any other dependency.
   *
   * @param artifacts Maven artifacts whose dependencies to retrieve
   * @return dependency graph representing the tree of Maven artifacts
   */
  public DependencyGraph buildFullDependencyGraph(List<Artifact> artifacts) {
    return buildFullDependencyGraph(artifacts, null);
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates, conflicting
   * versions, and provided and optional dependencies. It uses {@code existingSession} to resolve
   * artifacts if the session is not null; otherwise it creates one.
   *
   * <p>In the event of I/O errors, missing artifacts, and other problems, it can return an
   * incomplete graph. Each node's dependencies are resolved recursively. The scope of a dependency
   * does not affect the scope of its children's dependencies. Provided and optional dependencies
   * are not treated differently than any other dependency.
   *
   * @param artifacts Maven artifacts whose dependencies to retrieve
   * @param existingSession the session to resolve artifacts if one already exists
   * @return dependency graph representing the tree of Maven artifacts
   */
  public DependencyGraph buildFullDependencyGraph(
      List<Artifact> artifacts, RepositorySystemSession existingSession) {
    ImmutableList<DependencyNode> dependencyNodes =
        artifacts.stream().map(DefaultDependencyNode::new).collect(toImmutableList());
    RepositorySystemSession session =
        existingSession != null
            ? existingSession
            : RepositoryUtility.newSessionForVerboseListDependency(system);
    return buildDependencyGraph(dependencyNodes, session);
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates and conflicting
   * versions, but not optional dependencies.
   *
   * <p>In the event of I/O errors, missing artifacts, and other problems, it can return an
   * incomplete graph. Each node's dependencies are resolved recursively. The scope of a dependency
   * does not affect the scope of its children's dependencies.
   *
   * @param artifacts Maven artifacts whose dependencies to retrieve
   * @param existingSession the session to resolve artifacts if one already exists
   * @return dependency graph representing the tree of Maven artifacts
   */
  public DependencyGraph buildVerboseDependencyGraph(
      List<Artifact> artifacts, @Nullable RepositorySystemSession existingSession) {
    ImmutableList<DependencyNode> dependencyNodes =
        artifacts.stream().map(DefaultDependencyNode::new).collect(toImmutableList());
    RepositorySystemSession session =
        existingSession != null
            ? existingSession
            : RepositoryUtility.newSessionForVerboseListDependency(system);
    return buildDependencyGraph(dependencyNodes, session);
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates and conflicting
   * versions, but not optional dependencies. In the event of I/O errors, missing
   * artifacts, and other problems, it can return an incomplete graph. Each node's dependencies are
   * resolved recursively. The scope of a dependency does not affect the scope of its children's
   * dependencies.
   *
   * @param artifacts Maven artifacts whose dependencies to retrieve
   * @return dependency graph representing the tree of Maven artifacts
   */
  public DependencyGraph buildVerboseDependencyGraph(List<Artifact> artifacts) {
    return buildVerboseDependencyGraph(artifacts, null);
  }

  /**
   * Finds the full compile time, transitive dependency graph including conflicting versions and
   * provided dependencies. It uses {@code existingSession} to resolve artifacts if the session is
   * not null; otherwise it creates one. This includes direct optional dependencies of the root node
   * but not optional dependencies of transitive dependencies.
   *
   * <p>In the event of I/O errors, missing artifacts, and other problems, it can return an
   * incomplete graph. Each node's dependencies are resolved recursively. The scope of a dependency
   * does not affect the scope of its children's dependencies. Provided and optional dependencies
   * are not treated differently than any other dependency.
   *
   * @param artifact the root
   * @return the graph as built by Maven before dependency mediation
   */
  public DependencyGraph buildVerboseDependencyGraph(Artifact artifact) {
    Dependency dependency = new Dependency(artifact, "compile");
    return buildVerboseDependencyGraph(dependency);
  }
  
  DependencyGraph buildVerboseDependencyGraph(Dependency dependency) {
    DefaultRepositorySystemSession session = RepositoryUtility.newSessionForVerboseDependency(system);
    ImmutableList<DependencyNode> roots = ImmutableList.of(new DefaultDependencyNode(dependency));
    return buildDependencyGraph(roots, session);
  }

  /**
   * Builds the transitive dependency graph as seen by Maven. It does not include duplicates and
   * conflicting versions. That is, this resolves conflicting versions by picking the first version
   * seen. This is how Maven normally operates. It does not contain provided-scope dependencies
   * of transitive dependencies. It does not contain optional dependencies of transitive
   * dependencies. In the event of I/O errors, missing artifacts, and other problems, it can
   * return an incomplete graph.
   */
  public DependencyGraph buildMavenDependencyGraph(Dependency dependency) {
    ImmutableList<DependencyNode> roots = ImmutableList.of(new DefaultDependencyNode(dependency));
    return buildDependencyGraph(roots, RepositoryUtility.newSessionForMaven(system));
  }

  private DependencyGraph buildDependencyGraph(
      List<DependencyNode> dependencyNodes, RepositorySystemSession session) {

    try {
      DependencyNode node = resolveCompileTimeDependencies(dependencyNodes, session);
      return DependencyGraph.from(node);
    } catch (DependencyResolutionException ex) {
      DependencyResult result = ex.getResult();
      DependencyGraph graph = DependencyGraph.from(result.getRoot());

      for (ArtifactResult artifactResult : result.getArtifactResults()) {
        Artifact resolvedArtifact = artifactResult.getArtifact();

        if (resolvedArtifact == null) {
          Artifact requestedArtifact = artifactResult.getRequest().getArtifact();
          graph.addUnresolvableArtifactProblem(requestedArtifact);
        }
      }
      
      return graph;
    }
  }

}
