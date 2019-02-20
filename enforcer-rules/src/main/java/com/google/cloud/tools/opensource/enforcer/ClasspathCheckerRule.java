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

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClasspathCheckReport;
import com.google.cloud.tools.opensource.classpath.ClasspathChecker;
import com.google.cloud.tools.opensource.classpath.JarLinkageReport;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.resolution.DependencyRequest;

/**
 * Classpath Checker Maven Enforcer Rule.
 */
public class ClasspathCheckerRule implements EnforcerRule {

  @Override
  public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
    Log log = helper.getLog();

    try {
      MavenProject project = (MavenProject) helper.evaluate("${project}");
      MavenSession session = (MavenSession) helper.evaluate("${session}");
      RepositorySystemSession repositorySystemSession = session.getRepositorySession();

      int dependencyCount = project.getDependencies().size();

      ImmutableList<Path> classpath = dependencyCount == 0 ?
          findBomClasspath(project, repositorySystemSession, helper) :
          findProjectClasspath(project, repositorySystemSession, helper);

      List<Path> artifactJarsInProject = classpath.subList(0, dependencyCount);
      ImmutableSet<Path> entryPoints = ImmutableSet.copyOf(artifactJarsInProject);

      try {
        ClasspathChecker classpathChecker = ClasspathChecker.create(classpath, entryPoints);
        ClasspathCheckReport linkageReport = classpathChecker.findLinkageErrors();
        int totalErrors = linkageReport.getJarLinkageReports().stream()
            .mapToInt(JarLinkageReport::getCauseToSourceClassesSize)
            .sum();
        if (totalErrors > 0) {
          log.info("Linkage error report:" + linkageReport);
          throw new EnforcerRuleException(
              "Failed while checking class path. See above detailed error message.");
        }
      } catch (IOException ex) {
        log.error("Failed to run Classpath Checker", ex);
      }
    } catch (ExpressionEvaluationException e) {
      throw new EnforcerRuleException(
          "Unable to lookup an expression " + e.getLocalizedMessage(), e);
    }
  }

  /**
   * Finds class path for {@code project}.
   */
  private ImmutableList<Path> findProjectClasspath(MavenProject project,
      RepositorySystemSession session, EnforcerRuleHelper helper) throws EnforcerRuleException {
    try {
      /*
      List<Artifact> artifacts = null;
      ClassPathBuilder.artifactsToDependencyPaths(artifacts);*/
      ProjectDependenciesResolver projectDependenciesResolver =
          helper.getComponent(ProjectDependenciesResolver.class);
      DependencyResolutionRequest dependencyResolutionRequest =
          new DefaultDependencyResolutionRequest(project, session);
      DependencyResolutionResult dependencyResolutionResult = projectDependenciesResolver
          .resolve(dependencyResolutionRequest);
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

  /**
   * Finds class path for BOM.
   */
  private ImmutableList<Path> findBomClasspath(MavenProject project) throws EnforcerRuleException {
    Artifact bom = RepositoryUtils.toArtifact(project.getArtifact());
    try {
      List<Artifact> bomMembers = RepositoryUtility.readBom(bom);
      return ClassPathBuilder.artifactsToClasspath(bomMembers);
    } catch (RepositoryException ex) {
      throw new EnforcerRuleException("Failed to collect dependency " + ex.getMessage(), ex);
    }
  }

  @Override
  public String getCacheId() {
    return null;
  }

  @Override
  public boolean isCacheable() {
    return false;
  }

  @Override
  public boolean isResultValid(EnforcerRule enforcerRule) {
    return false;
  }
}
