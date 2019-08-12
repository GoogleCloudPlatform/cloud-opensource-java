/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.dependencies.enforcer;

import static com.google.cloud.tools.opensource.dependencies.RepositoryUtility.shouldSkipBomMember;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.apache.maven.enforcer.rule.api.EnforcerLevel.WARN;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassReferenceGraph;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.FilteringZipDependencySelector;
import com.google.cloud.tools.opensource.dependencies.NonTestDependencySelector;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.enforcer.AbstractNonCacheableEnforcerRule;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;

/** Linkage Checker Maven Enforcer Rule. */
public class LinkageCheckerRule extends AbstractNonCacheableEnforcerRule {

  /**
   * Maven packaging values known to be irrelevant to Linkage Check for non-BOM project.
   *
   * @see <a href="https://maven.apache.org/ref/3.6.1/maven-core/artifact-handlers.html">Maven
   * Core: Default Artifact Handlers Reference</a>
   */
  private static final ImmutableSet<String> UNSUPPORTED_NONBOM_PACKAGING = ImmutableSet.of("pom",
      "java-source", "javadoc");

  /**
   * The section this rule reads dependencies from. By default, it's {@link
   * DependencySection#DEPENDENCIES}.
   */
  private DependencySection dependencySection = DependencySection.DEPENDENCIES;

  /**
   * Set to true to suppress linkage errors unreachable from the classes in the direct dependencies.
   * By default, it's {@code false}.
   *
   * @see <a href=
   *     "https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#class-reference-graph"
   *     >Java Dependency Glossary: Class reference graph</a>
   */
  private boolean reportOnlyReachable = false;

  @VisibleForTesting
  void setDependencySection(DependencySection dependencySection) {
    this.dependencySection = dependencySection;
  }

  @VisibleForTesting
  void setReportOnlyReachable(boolean reportOnlyReachable) {
    this.reportOnlyReachable = reportOnlyReachable;
  }

  @VisibleForTesting
  enum DependencySection {
    /** To read {@code dependencyManagement} section in pom.xml. This is for BOM projects */
    DEPENDENCY_MANAGEMENT,
    /** To read {@code dependencies} section in pom.xml. This is for library users' projects */
    DEPENDENCIES
  }

  private Log logger;

  @Override
  public void execute(@Nonnull EnforcerRuleHelper helper) throws EnforcerRuleException {
    logger = helper.getLog();

    try {
      MavenProject project = (MavenProject) helper.evaluate("${project}");
      MavenSession session = (MavenSession) helper.evaluate("${session}");
      RepositorySystemSession repositorySystemSession = session.getRepositorySession();

      boolean readingDependencyManagementSection =
          dependencySection == DependencySection.DEPENDENCY_MANAGEMENT;
      if (readingDependencyManagementSection
          && (project.getDependencyManagement() == null
              || project.getDependencyManagement().getDependencies() == null
              || project.getDependencyManagement().getDependencies().isEmpty())) {
        logger.warn("The rule is set to read dependency management section but it is empty.");
      }

      String projectType = project.getArtifact().getType();
      if (readingDependencyManagementSection) {
        if (!"pom".equals(projectType)) {
          logger.warn("A BOM should have packaging pom");
          return;
        }
      } else {
        if (UNSUPPORTED_NONBOM_PACKAGING.contains(projectType)) {
          return;
        }
      }

      ImmutableList<Path> classpath =
          readingDependencyManagementSection
              ? findBomClasspath(project, repositorySystemSession)
              : findProjectClasspath(project, repositorySystemSession, helper);
      if (classpath.isEmpty()) {
        logger.warn("Class path is empty.");
        return;
      }

      // As sorted by level order, the first elements in classpath are the project and its direct
      // non-test dependencies.
      long projectDependencyCount = project.getDependencies().stream()
                  .filter(dependency -> !"test".equals(dependency.getScope()))
                  .count();
      List<Path> entryPoints = classpath.subList(0, (int) projectDependencyCount + 1);

      try {

        // TODO LinkageChecker.create and LinkageChecker.findSymbolProblems
        // should not be two separate public methods since we all call
        // findSymbolProblems immediately after create
        LinkageChecker linkageChecker = LinkageChecker.create(classpath, entryPoints);
        ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
            linkageChecker.findSymbolProblems();
        if (reportOnlyReachable) {
          ClassReferenceGraph classReferenceGraph = linkageChecker.getClassReferenceGraph();
          symbolProblems =
              symbolProblems.entries().stream()
                  .filter(entry -> classReferenceGraph.isReachable(entry.getValue().getClassName()))
                  .collect(
                      ImmutableSetMultimap.toImmutableSetMultimap(Entry::getKey, Entry::getValue));
        }
        // Count unique SymbolProblems
        int errorCount = symbolProblems.keySet().size();

        String foundError = reportOnlyReachable ? "reachable error" : "error";
        if (errorCount > 1) {
          foundError += "s";
        }
        if (errorCount > 0) {
          String message =
              String.format(
                  "Linkage Checker rule found %d %s. Linkage error report:\n%s",
                  errorCount, foundError, SymbolProblem.formatSymbolProblems(symbolProblems));
          if (getLevel() == WARN) {
            logger.warn(message);
          } else {
            logger.error(message);
            throw new EnforcerRuleException(
                "Failed while checking class path. See above error report.");
          }
        } else {
          // arguably shouldn't log anything on success
          logger.info("No " + foundError + " found");
        }
      } catch (IOException ex) {
        // Maven's "-e" flag does not work for EnforcerRuleException. Print stack trace here.
        logger.warn("Failed to run Linkage Checker", ex);
        return; // Not failing the build.
      }
    } catch (ExpressionEvaluationException ex) {
      throw new EnforcerRuleException("Unable to lookup an expression " + ex.getMessage(), ex);
    }
  }

  /** Builds a class path for {@code mavenProject}. */
  private ImmutableList<Path> findProjectClasspath(
      MavenProject mavenProject, RepositorySystemSession session, EnforcerRuleHelper helper)
      throws EnforcerRuleException {
    try {
      ProjectDependenciesResolver projectDependenciesResolver =
          helper.getComponent(ProjectDependenciesResolver.class);
      DefaultRepositorySystemSession fullDependencyResolutionSession =
          new DefaultRepositorySystemSession(session);

      // For netty-handler referencing its dependencies with ${os.detected.classifier}
      Map<String, String> properties = new HashMap<>(); // allowing duplicate entries
      properties.putAll(fullDependencyResolutionSession.getSystemProperties());
      properties.putAll(DependencyGraphBuilder.detectOsProperties());
      fullDependencyResolutionSession.setSystemProperties(properties);

      fullDependencyResolutionSession.setDependencySelector(
          new AndDependencySelector(
              new NonTestDependencySelector(),
              new ExclusionDependencySelector(),
              new FilteringZipDependencySelector()));
      DependencyResolutionRequest dependencyResolutionRequest =
          new DefaultDependencyResolutionRequest(mavenProject, fullDependencyResolutionSession);

      DependencyResolutionResult resolutionResult =
          projectDependenciesResolver.resolve(dependencyResolutionRequest);

      return buildClasspath(resolutionResult);
    } catch (ComponentLookupException e) {
      throw new EnforcerRuleException("Unable to lookup a component " + e.getMessage(), e);
    } catch (DependencyResolutionException e) {
      return buildClasspathFromException(e);
    }
  }

  /**
   * Returns class path built from partial dependency graph of {@code resolutionException}.
   *
   * @throws EnforcerRuleException when {@code resolutionException} is invalidated by {@link
   *     DependencyGraphBuilder#requiredDependency(List)}
   */
  private ImmutableList<Path> buildClasspathFromException(
      DependencyResolutionException resolutionException) throws EnforcerRuleException {
    DependencyResolutionResult result = resolutionException.getResult();
    DependencyNode root = result.getDependencyGraph();

    for (Throwable cause = resolutionException.getCause();
        cause != null;
        cause = cause.getCause()) {
      if (cause instanceof ArtifactTransferException) {
        ArtifactTransferException artifactException = (ArtifactTransferException) cause;
        Artifact artifact = artifactException.getArtifact();
        String pathsToArtifact = findPaths(root, artifact);
        ImmutableList<DependencyNode> firstArtifactPath =
            Iterables.getFirst(findArtifactPaths(root, artifact), null);
        if (DependencyGraphBuilder.requiredDependency(firstArtifactPath)) {
          logger.error("Could not find artifact " + artifact);
          logger.error("Paths to the missing artifact: " + pathsToArtifact);
          throw new EnforcerRuleException(
              "Unable to build a dependency graph: " + resolutionException.getMessage(),
              resolutionException);
        } else {
          logger.warn(
              "There was missing artifact at "
                  + pathsToArtifact
                  + ". Continuing with partial dependency graph.");
        }
        break;
      }
    }
    // The exception is acceptable enough to build a class path.
    return buildClasspath(result);
  }

  private ImmutableList<Path> buildClasspath(DependencyResolutionResult result)
      throws EnforcerRuleException {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();

    // The root node must have the project's JAR file
    File rootFile = result.getDependencyGraph().getArtifact().getFile();
    if (rootFile == null) {
      throw new EnforcerRuleException(
          "The root project artifact is not associated with a file."
              + " The linkage checker enforcer rule should be bound to the 'verify' phase.");
    }
    builder.add(rootFile.toPath());
    // The rest are the dependencies
    for (Dependency dependency : result.getResolvedDependencies()) {
      // Resolved dependencies are guaranteed to have file.
      builder.add(dependency.getArtifact().getFile().toPath());
    }
    return builder.build();
  }

  /** Builds a class path for {@code bomProject}. */
  private ImmutableList<Path> findBomClasspath(
      MavenProject bomProject, RepositorySystemSession repositorySystemSession)
      throws EnforcerRuleException {

    ArtifactTypeRegistry artifactTypeRegistry = repositorySystemSession.getArtifactTypeRegistry();
    try {
      ImmutableList<Artifact> artifacts =
          bomProject.getDependencyManagement().getDependencies().stream()
              .map(dependency -> RepositoryUtils.toDependency(dependency, artifactTypeRegistry))
              .map(Dependency::getArtifact)
              .filter(artifact -> !shouldSkipBomMember(artifact))
              .collect(toImmutableList());
      return ClassPathBuilder.artifactsToClasspath(artifacts);
    } catch (RepositoryException ex) {
      throw new EnforcerRuleException("Failed to collect dependency " + ex.getMessage(), ex);
    }
  }

  private static String findPaths(DependencyNode root, Artifact artifact) {
    ImmutableList<ImmutableList<DependencyNode>> dependencyPaths =
        findArtifactPaths(root, artifact);

    ImmutableList<String> paths =
        dependencyPaths.stream()
            .map(path -> Joiner.on(" > ").join(path))
            .collect(toImmutableList());
    // Joining one or more paths from root to the artifact
    return Joiner.on("\n").join(paths);
  }

  private static ImmutableList<ImmutableList<DependencyNode>> findArtifactPaths(
      DependencyNode node, Artifact artifact) {
    Deque<DependencyNode> stack = new ArrayDeque<>();
    stack.addLast(node);
    ImmutableList.Builder<ImmutableList<DependencyNode>> builder = ImmutableList.builder();
    findArtifact(builder, node, stack, artifact);
    return builder.build();
  }

  private static void findArtifact(
      ImmutableList.Builder<ImmutableList<DependencyNode>> result,
      DependencyNode node,
      Deque<DependencyNode> path,
      Artifact artifact) {
    if (Artifacts.toCoordinates(node.getArtifact()).equals(Artifacts.toCoordinates(artifact))) {
      result.add(ImmutableList.copyOf(path));
    }
    for (DependencyNode child : node.getChildren()) {
      path.addLast(child);
      findArtifact(result, child, path, artifact);
      path.removeLast();
    }
  }
}
