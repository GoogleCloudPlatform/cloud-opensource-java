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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.cloud.tools.opensource.enforcer.LinkageCheckerRule.DependencySection;
import com.google.common.collect.ImmutableList;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
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
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LinkageCheckerRuleTest {
  private LinkageCheckerRule rule;
  private RepositorySystem repositorySystem;
  private RepositorySystemSession repositorySystemSession;

  private MavenProject mockProject;
  private EnforcerRuleHelper mockRuleHelper;
  private MavenSession mockMavenSession;
  private ProjectDependenciesResolver mockProjectDependenciesResolver;
  private DependencyResolutionResult mockDependencyResolutionResult;

  @Before
  public void setup() {
    rule = new LinkageCheckerRule();
    repositorySystem = RepositoryUtility.newRepositorySystem();
    repositorySystemSession = RepositoryUtility.newSession(repositorySystem);
  }

  @Before
  public void setupMock()
      throws ExpressionEvaluationException, ComponentLookupException,
          DependencyResolutionException {
    mockProject = mock(MavenProject.class);
    mockMavenSession = mock(MavenSession.class);
    mockRuleHelper = mock(EnforcerRuleHelper.class);
    mockProjectDependenciesResolver = mock(ProjectDependenciesResolver.class);
    mockDependencyResolutionResult = mock(DependencyResolutionResult.class);
    when(mockRuleHelper.getLog()).thenReturn(mock(Log.class));
    when(mockRuleHelper.getComponent(ProjectDependenciesResolver.class))
        .thenReturn(mockProjectDependenciesResolver);
    when(mockProjectDependenciesResolver.resolve(any(DependencyResolutionRequest.class)))
        .thenReturn(mockDependencyResolutionResult);
    when(mockRuleHelper.evaluate("${session}")).thenReturn(mockMavenSession);
    when(mockRuleHelper.evaluate("${project}")).thenReturn(mockProject);
  }

  /**
   * Returns a list of {@link Dependency}s resolved from {@link Artifact} of {@code coordinates}
   * with the file in local Maven repository.
   */
  private ImmutableList<Dependency> createResolvedDependency(String coordinates)
      throws RepositoryException {
    Artifact artifact = new DefaultArtifact(coordinates);
    Dependency dependency = new Dependency(artifact, "compile");

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRepositories(ImmutableList.of(RepositoryUtility.CENTRAL));
    collectRequest.setRoot(dependency);
    DependencyNode dependencyNode =
        repositorySystem.collectDependencies(repositorySystemSession, collectRequest).getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(dependencyNode);
    DependencyResult dependencyResult =
        repositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);
    return dependencyResult.getArtifactResults().stream()
        .map(ArtifactResult::getRequest)
        .map(ArtifactRequest::getDependencyNode)
        .map(DependencyNode::getDependency)
        .collect(toImmutableList());
  }

  private void setupMockDependencyResolution(String coordinates) throws RepositoryException {
    ImmutableList<Dependency> dummyDependencies = createResolvedDependency(coordinates);
    when(mockDependencyResolutionResult.getDependencies()).thenReturn(dummyDependencies);
  }

  @Test
  public void testExecute_shouldPassGoodProject()
      throws EnforcerRuleException, RepositoryException {
    // Since Guava 27, it requires com.google.guava:failureaccess artifact in its dependency.
    setupMockDependencyResolution("com.google.guava:guava:27.0.1-jre");
    // This should not raise an EnforcerRuleException
    rule.execute(mockRuleHelper);
  }

  @Test
  public void testExecute_shouldFailForBadProject() throws RepositoryException {
    try {
      // This artifact is known to contain classes missing dependencies
      setupMockDependencyResolution("com.google.appengine:appengine-api-1.0-sdk:1.9.64");
      rule.execute(mockRuleHelper);
      Assert.fail(
          "The rule should raise an EnforcerRuleException for artifacts missing dependencies");
    } catch (EnforcerRuleException ex) {
      // pass
      Assert.assertEquals(
          "Failed while checking class path. See above error report.", ex.getMessage());
    }
  }

  @Test
  public void testExecute_shouldPassEmptyBom() throws EnforcerRuleException {
    rule.setDependencySection(DependencySection.DEPENDENCY_MANAGEMENT);
    org.apache.maven.artifact.DefaultArtifact bomArtifact =
        new org.apache.maven.artifact.DefaultArtifact(
            "com.google.dummy",
            "dummy-bom",
            "0.1",
            "compile",
            "jar",
            "",
            new DefaultArtifactHandler());
    when(mockProject.getArtifact()).thenReturn(bomArtifact);

    // This should not raise an EnforcerRuleException
    rule.execute(mockRuleHelper);
  }
}
