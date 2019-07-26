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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

  private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();

  private static final CharMatcher LOWER_ALPHA_NUMERIC =
      CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9'));

  static {
    setDetectedOsSystemProperties();
  }

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

  private static DependencyNode resolveCompileTimeDependencies(
      List<Artifact> dependencyArtifacts,
      boolean completeDependencyTree,
      boolean includeProvidedScope)
      throws DependencyCollectionException, DependencyResolutionException {

    RepositorySystemSession session;
    if (completeDependencyTree) {
      session = RepositoryUtility.newSessionWithDuplicateArtifacts(system, includeProvidedScope);
    } else {
      session = RepositoryUtility.newSession(system);
    }

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
    CollectResult collectResult = system.collectDependencies(session, collectRequest);
    DependencyNode node = collectResult.getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(node);
    dependencyRequest.setCollectRequest(collectRequest);

    // This might be able to speed up by using collectDependencies here instead
    system.resolveDependencies(session, dependencyRequest);

    return node;
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
    DependencyNode root = resolveCompileTimeDependencies(artifacts, true, true);
    return new DependencyGraph(root);
  }

  private static DependencyNode resolveCompileTimeDependenciesWithoutProvided(
      Artifact rootDependencyArtifact, boolean completeDependencyTree)
      throws DependencyCollectionException, DependencyResolutionException {
    // Dashboard's dependency convergence does not need dependencies with provided scope.
    return resolveCompileTimeDependencies(
        ImmutableList.of(rootDependencyArtifact), completeDependencyTree, false);
  }

  /** Returns the non-transitive compile time dependencies of an artifact. */
  static List<Artifact> getDirectDependencies(Artifact artifact) throws RepositoryException {

    List<Artifact> result = new ArrayList<>();

    DependencyNode root = resolveCompileTimeDependenciesWithoutProvided(artifact, false);
    for (DependencyNode child : root.getChildren()) {
      result.add(child.getArtifact());
    }
    return result;
  }

  /**
   * Finds the full compile time, transitive dependency graph including duplicates and conflicting
   * versions.
   */
  public static DependencyGraph getCompleteDependencies(Artifact artifact)
      throws RepositoryException {
    DependencyNode root = resolveCompileTimeDependenciesWithoutProvided(artifact, true);
    return new DependencyGraph(root);
  }

  /**
   * Finds the complete transitive dependency graph as seen by Maven. It does not include duplicates
   * and conflicting versions. That is, this resolves conflicting versions by picking the first
   * version seen. This is how Maven normally operates.
   */
  public static DependencyGraph getTransitiveDependencies(Artifact artifact)
      throws RepositoryException {
    DependencyNode root = resolveCompileTimeDependenciesWithoutProvided(artifact, false);
    return new DependencyGraph(root);
  }
}
