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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.apache.maven.enforcer.rule.api.EnforcerLevel.WARN;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClasspathCheckReport;
import com.google.cloud.tools.opensource.classpath.ClasspathChecker;
import com.google.cloud.tools.opensource.classpath.JarLinkageReport;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
import org.eclipse.aether.graph.Dependency;

/** Linkage Checker Maven Enforcer Rule. */
public class LinkageCheckerRule extends AbstractNonCacheableEnforcerRule {

  /**
   * The section this rule reads dependencies from. By default, it's {@link
   * DependencySection#DEPENDENCIES}.
   */
  private DependencySection dependencySection = DependencySection.DEPENDENCIES;

  @VisibleForTesting
  void setDependencySection(DependencySection dependencySection) {
    this.dependencySection = dependencySection;
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
              ? findBomClasspath(project)
              : findProjectClasspath(project, repositorySystemSession, helper);
      if (classpath.isEmpty()) {
        logger.warn("Class path is empty.");
        return;
      }

      List<Path> artifactJarsInProject = classpath.subList(0, project.getDependencies().size());
      ImmutableSet<Path> entryPoints = ImmutableSet.copyOf(artifactJarsInProject);

      try {
        ClasspathChecker classpathChecker = ClasspathChecker.create(classpath, entryPoints);
        ClasspathCheckReport linkageReport = classpathChecker.findLinkageErrors();
        int totalErrors =
            linkageReport.getJarLinkageReports().stream()
                .mapToInt(JarLinkageReport::getCauseToSourceClassesSize)
                .sum();
        if (totalErrors > 0) {
          if (getLevel() == WARN) {
            logger.warn(
                "Linkage Checker rule found non-zero errors. Linkage error report:\n"
                    + linkageReport);
          } else {
            logger.error(
                "Linkage Checker rule found non-zero errors. Linkage error report:\n"
                    + linkageReport);
          }
          throw new EnforcerRuleException(
              "Failed while checking class path. See above error report.");
        } else {
          logger.info("No linkage error found");
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
      return dependencyResolutionResult.getDependencies().stream()
          .map(Dependency::getArtifact)
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
  private ImmutableList<Path> findBomClasspath(MavenProject bomProject)
      throws EnforcerRuleException {
    Artifact bom = RepositoryUtils.toArtifact(bomProject.getArtifact());
    try {
      List<Artifact> bomMembers = RepositoryUtility.readBom(bom);
      return ClassPathBuilder.artifactsToClasspath(bomMembers);
    } catch (RepositoryException ex) {
      throw new EnforcerRuleException("Failed to collect dependency " + ex.getMessage(), ex);
    }
  }
}
