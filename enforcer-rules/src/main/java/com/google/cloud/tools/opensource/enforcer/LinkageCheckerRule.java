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

package com.google.cloud.tools.opensource.enforcer;

import static com.google.cloud.tools.opensource.dependencies.RepositoryUtility.shouldSkipBomMember;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.apache.maven.enforcer.rule.api.EnforcerLevel.WARN;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.JarLinkageReport;
import com.google.cloud.tools.opensource.classpath.LinkageCheckReport;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.Traverser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

/** Linkage Checker Maven Enforcer Rule. */
public class LinkageCheckerRule extends AbstractNonCacheableEnforcerRule {

  /**
   * The section this rule reads dependencies from. By default, it's {@link
   * DependencySection#DEPENDENCIES}.
   */
  private DependencySection dependencySection = DependencySection.DEPENDENCIES;

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

  @Override
  public void execute(@Nonnull EnforcerRuleHelper helper) throws EnforcerRuleException {
    Log logger = helper.getLog();

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

      ImmutableList<Path> classpath =
          readingDependencyManagementSection
              ? findBomClasspath(project, repositorySystemSession)
              : findProjectClasspath(project, repositorySystemSession, helper);
      if (classpath.isEmpty()) {
        logger.warn("Class path is empty.");
        return;
      }

      // As sorted by level order, the first elements in classpath is direct dependencies.
      List<Path> directDependencies = classpath.subList(0, project.getDependencies().size());

      try {
        LinkageChecker linkageChecker = LinkageChecker.create(classpath, directDependencies);
        LinkageCheckReport linkageReport = linkageChecker.findLinkageErrors();
        long errorCount =
            linkageReport.getJarLinkageReports().stream()
                .mapToInt(JarLinkageReport::getCauseToSourceClassesSize)
                .sum();
        if (reportOnlyReachable) {
          ImmutableList<JarLinkageReport> reachableErrorReports =
              linkageReport.getJarLinkageReports().stream()
                  .map(JarLinkageReport::reachableErrors)
                  .collect(toImmutableList());
          linkageReport = LinkageCheckReport.create(reachableErrorReports);
          long reachableErrorCount =
              reachableErrorReports.stream()
                  .mapToInt(JarLinkageReport::getCauseToSourceClassesSize)
                  .sum();
          errorCount = reachableErrorCount;
        }

        String foundError =
            String.format(
                "%serror%s", reportOnlyReachable ? "reachable " : "", errorCount > 1 ? "s" : "");
        if (errorCount > 0) {
          String message =
              "Linkage Checker rule found "
                  + foundError
                  + ". Linkage error report:\n"
                  + linkageReport;
          if (getLevel() == WARN) {
            logger.warn(message);
          } else {
            logger.error(message);
          }
          throw new EnforcerRuleException(
              "Failed while checking class path. See above error report.");
        } else {
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
      DependencyResolutionRequest dependencyResolutionRequest =
          new DefaultDependencyResolutionRequest(mavenProject, session);
      DependencyResolutionResult dependencyResolutionResult =
          projectDependenciesResolver.resolve(dependencyResolutionRequest);
      Traverser<DependencyNode> traverser = Traverser.forTree(node -> node.getChildren());
      return ImmutableList.copyOf(
              traverser.breadthFirst(dependencyResolutionResult.getDependencyGraph()))
          .stream()
          .map(DependencyNode::getArtifact)
          .filter(Objects::nonNull)
          .map(Artifact::getFile)
          .map(File::toPath)
          .collect(toImmutableList());
    } catch (ComponentLookupException e) {
      throw new EnforcerRuleException("Unable to lookup a component " + e.getLocalizedMessage(), e);
    } catch (DependencyResolutionException e) {
      throw new EnforcerRuleException("Unable to build a dependency graph", e);
    }
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
}
