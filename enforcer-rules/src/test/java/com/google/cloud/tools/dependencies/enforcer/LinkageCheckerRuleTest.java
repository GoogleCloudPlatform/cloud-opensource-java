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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.dependencies.enforcer.LinkageCheckerRule.DependencySection;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.Traverser;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.enforcer.rule.api.EnforcerLevel;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DependencyManagement;
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
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

public class LinkageCheckerRuleTest {

  private LinkageCheckerRule rule;
  private RepositorySystem repositorySystem;
  private RepositorySystemSession repositorySystemSession;

  private MavenProject mockProject;
  private EnforcerRuleHelper mockRuleHelper;
  private Log mockLog;
  private MavenSession mockMavenSession;
  private ProjectDependenciesResolver mockProjectDependenciesResolver;
  private DependencyResolutionResult mockDependencyResolutionResult;

  @Before
  public void setup()
      throws ExpressionEvaluationException, ComponentLookupException,
          DependencyResolutionException {
    rule = new LinkageCheckerRule();
    repositorySystem = RepositoryUtility.newRepositorySystem();
    repositorySystemSession = RepositoryUtility.newSession(repositorySystem);
    setupMock();
  }

  private void setupMock()
      throws ExpressionEvaluationException, ComponentLookupException,
          DependencyResolutionException {
    mockProject = mock(MavenProject.class);
    mockMavenSession = mock(MavenSession.class);
    when(mockMavenSession.getRepositorySession()).thenReturn(repositorySystemSession);
    mockRuleHelper = mock(EnforcerRuleHelper.class);
    mockProjectDependenciesResolver = mock(ProjectDependenciesResolver.class);
    mockDependencyResolutionResult = mock(DependencyResolutionResult.class);
    mockLog = mock(Log.class);
    when(mockRuleHelper.getLog()).thenReturn(mockLog);
    when(mockRuleHelper.getComponent(ProjectDependenciesResolver.class))
        .thenReturn(mockProjectDependenciesResolver);
    when(mockProjectDependenciesResolver.resolve(any(DependencyResolutionRequest.class)))
        .thenReturn(mockDependencyResolutionResult);
    when(mockRuleHelper.evaluate("${session}")).thenReturn(mockMavenSession);
    when(mockRuleHelper.evaluate("${project}")).thenReturn(mockProject);
    when(mockProject.getArtifact())
        .thenReturn(
            new org.apache.maven.artifact.DefaultArtifact(
                "com.google.cloud",
                "linkage-checker-rule-test",
                "0.0.1",
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler()));
  }

  /** Returns a dependency graph node resolved from {@link Artifact} of {@code coordinates}. */
  private DependencyNode createResolvedDependencyGraph(String... coordinates)
      throws RepositoryException, URISyntaxException {
    CollectRequest collectRequest = new CollectRequest();
    // This dummy artifact must be something that exists in a repository
    Artifact dummyRootArtifact = new DefaultArtifact("com.google.guava:guava:28.0-android");
    collectRequest.setRootArtifact(
        dummyRootArtifact.setFile(
            Paths.get(URLClassLoader.getSystemResource("dummy-0.0.1.jar").toURI()).toFile()));
    collectRequest.setRepositories(ImmutableList.of(RepositoryUtility.CENTRAL));
    collectRequest.setDependencies(
        Arrays.stream(coordinates)
            .map(DefaultArtifact::new)
            .map(artifact -> new Dependency(artifact, "compile"))
            .collect(toImmutableList()));
    DependencyNode dependencyNode =
        repositorySystem.collectDependencies(repositorySystemSession, collectRequest).getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(dependencyNode);
    DependencyResult dependencyResult =
        repositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);

    return dependencyResult.getRoot();
  }

  private void setupMockDependencyResolution(String... coordinates)
      throws RepositoryException, URISyntaxException {
    DependencyNode rootNode = createResolvedDependencyGraph(coordinates);
    Traverser<DependencyNode> traverser = Traverser.forGraph(node -> node.getChildren());

    // DependencyResolutionResult.getDependencies returns depth-first order
    ImmutableList<Dependency> dummyDependencies =
        ImmutableList.copyOf(traverser.depthFirstPreOrder(rootNode)).stream()
            .map(DependencyNode::getDependency)
            .filter(Objects::nonNull)
            .collect(toImmutableList());
    when(mockDependencyResolutionResult.getDependencies()).thenReturn(dummyDependencies);
    when(mockDependencyResolutionResult.getDependencyGraph()).thenReturn(rootNode);
    when(mockProject.getDependencies())
        .thenReturn(
            dummyDependencies.subList(0, coordinates.length).stream()
                .map(LinkageCheckerRuleTest::toDependency)
                .collect(Collectors.toList()));
  }

  @Test
  public void testExecute_shouldPassGoodProject()
      throws EnforcerRuleException, RepositoryException, URISyntaxException {
    // Since Guava 27, it requires com.google.guava:failureaccess artifact in its dependency.
    setupMockDependencyResolution("com.google.guava:guava:27.0.1-jre");
    // This should not raise an EnforcerRuleException
    rule.execute(mockRuleHelper);
    verify(mockLog).info("No error found");
  }

  @Test
  public void testExecute_shouldFailForBadProject() throws RepositoryException, URISyntaxException {
    try {
      // This artifact is known to contain classes missing dependencies
      setupMockDependencyResolution("com.google.appengine:appengine-api-1.0-sdk:1.9.64");
      rule.execute(mockRuleHelper);
      Assert.fail(
          "The rule should raise an EnforcerRuleException for artifacts missing dependencies");
    } catch (EnforcerRuleException ex) {
      // pass
      verify(mockLog).error(ArgumentMatchers.startsWith("Linkage Checker rule found 112 errors."));
      assertEquals("Failed while checking class path. See above error report.", ex.getMessage());
    }
  }

  @Test
  public void testExecute_shouldFailForBadProject_reachableErrors()
      throws RepositoryException, URISyntaxException {
    try {
      // This pair of artifacts contains linkage errors on grpc-core's use of Verify. Because
      // grpc-core is included in entry point jars, the errors are reachable.
      setupMockDependencyResolution(
          "com.google.api-client:google-api-client:1.27.0", "io.grpc:grpc-core:1.17.1");
      rule.setReportOnlyReachable(true);
      rule.execute(mockRuleHelper);
      Assert.fail(
          "The rule should raise an EnforcerRuleException for artifacts with reachable errors");
    } catch (EnforcerRuleException ex) {
      // pass
      verify(mockLog)
          .error(ArgumentMatchers.startsWith("Linkage Checker rule found 1 reachable error."));
      assertEquals(
          "Failed while checking class path. See above error report.", ex.getMessage());
    }
  }

  @Test
  public void testExecute_shouldPassForBadProject_levelWarn()
      throws RepositoryException, EnforcerRuleException, URISyntaxException {
    // This pair of artifacts contains linkage errors on grpc-core's use of Verify. Because
    // grpc-core is included in entry point jars, the errors are reachable.
    setupMockDependencyResolution(
        "com.google.api-client:google-api-client:1.27.0", "io.grpc:grpc-core:1.17.1");
    rule.setReportOnlyReachable(true);
    rule.setLevel(EnforcerLevel.WARN);
    rule.execute(mockRuleHelper);
    verify(mockLog)
        .warn(ArgumentMatchers.startsWith("Linkage Checker rule found 1 reachable error."));
  }

  @Test
  public void testExecute_shouldPassGoodProject_unreachableErrors()
      throws EnforcerRuleException, RepositoryException, URISyntaxException {
    // This artifact has transitive dependency on grpc-netty-shaded, which has linkage errors for
    // missing classes. They are all unreachable.
    setupMockDependencyResolution("com.google.cloud:google-cloud-automl:0.81.0-beta");
    rule.setReportOnlyReachable(true);
    // This should not raise EnforcerRuleException because the linkage errors are unreachable.
    rule.execute(mockRuleHelper);
  }

  private void setupMockDependencyManagementSection(String... coordinates) {
    org.apache.maven.artifact.DefaultArtifact bomArtifact =
        new org.apache.maven.artifact.DefaultArtifact(
            "com.google.dummy",
            "dummy-bom",
            "0.1",
            "compile",
            "pom",
            "",
            new DefaultArtifactHandler());
    when(mockProject.getArtifact()).thenReturn(bomArtifact);

    DependencyManagement mockDependencyManagement = mock(DependencyManagement.class);
    when(mockProject.getDependencyManagement()).thenReturn(mockDependencyManagement);

    ImmutableList<org.apache.maven.model.Dependency> bomMembers =
        Arrays.stream(coordinates)
            .map(DefaultArtifact::new)
            .map(artifact -> new Dependency(artifact, "compile"))
            .map(LinkageCheckerRuleTest::toDependency)
            .collect(toImmutableList());
    when(mockDependencyManagement.getDependencies()).thenReturn(bomMembers);

    when(mockMavenSession.getRepositorySession()).thenReturn(repositorySystemSession);
  }

  private static org.apache.maven.model.Dependency toDependency(Dependency resolvedDependency) {
    org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
    Artifact artifact = resolvedDependency.getArtifact();
    dependency.setArtifactId(artifact.getArtifactId());
    dependency.setGroupId(artifact.getGroupId());
    dependency.setVersion(artifact.getVersion());
    dependency.setOptional(dependency.isOptional());
    dependency.setClassifier(artifact.getClassifier());
    dependency.setExclusions(dependency.getExclusions());
    dependency.setScope(dependency.getScope());
    return dependency;
  }

  @Test
  public void testExecute_shouldPassEmptyBom() throws EnforcerRuleException {
    rule.setDependencySection(DependencySection.DEPENDENCY_MANAGEMENT);
    setupMockDependencyManagementSection(); // empty BOM

    // This should not raise an EnforcerRuleException
    rule.execute(mockRuleHelper);
  }

  @Test
  public void testExecute_shouldPassGoodBom() throws EnforcerRuleException {
    rule.setDependencySection(DependencySection.DEPENDENCY_MANAGEMENT);
    setupMockDependencyManagementSection(
        "com.google.guava:guava:27.0.1-android",
        "io.grpc:grpc-auth:1.18.0",
        "com.google.api:api-common:1.7.0");
    // This should not raise an EnforcerRuleException
    rule.execute(mockRuleHelper);
  }

  @Test
  public void testExecute_shouldFailBadBom() {
    rule.setDependencySection(DependencySection.DEPENDENCY_MANAGEMENT);
    setupMockDependencyManagementSection(
        "com.google.api-client:google-api-client:1.27.0", "io.grpc:grpc-core:1.17.1");

    try {
      rule.execute(mockRuleHelper);
      Assert.fail("Enforcer rule should detect conflict between google-api-client and grpc-core");
    } catch (EnforcerRuleException ex) {
      // pass
    }
  }

  @Test
  public void testExecute_shouldSkipParentPom() throws EnforcerRuleException {
    when(mockProject.getArtifact())
        .thenReturn(
            new org.apache.maven.artifact.DefaultArtifact(
                "com.google.cloud",
                "linkage-checker-rule-parent",
                "0.0.1",
                "compile",
                "pom",
                null,
                new DefaultArtifactHandler()));
    rule.execute(mockRuleHelper);
    verify(mockLog).debug(ArgumentMatchers.eq("Skipping project type pom"));
  }
}
