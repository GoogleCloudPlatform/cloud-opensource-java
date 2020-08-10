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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.dependencies.enforcer.LinkageCheckerRule.DependencySection;
import com.google.cloud.tools.opensource.dependencies.OsProperties;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.Traverser;
import com.google.common.truth.Truth;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.enforcer.rule.api.EnforcerLevel;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecution;
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
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

public class LinkageCheckerRuleTest {

  private LinkageCheckerRule rule = new LinkageCheckerRule();
  private RepositorySystem repositorySystem;
  private RepositorySystemSession repositorySystemSession;
  private Artifact dummyArtifactWithFile;

  private MavenProject mockProject;
  private EnforcerRuleHelper mockRuleHelper;
  private Log mockLog;
  private MavenSession mockMavenSession;
  private MojoExecution mockMojoExecution;
  private ProjectDependenciesResolver mockProjectDependenciesResolver;
  private DependencyResolutionResult mockDependencyResolutionResult;

  @Before
  public void setup()
      throws ExpressionEvaluationException, ComponentLookupException,
      DependencyResolutionException, URISyntaxException {
    repositorySystem = RepositoryUtility.newRepositorySystem();
    repositorySystemSession = RepositoryUtility.newSession(repositorySystem);
    dummyArtifactWithFile = createArtifactWithDummyFile("a:b:0.1");
    setupMock();
  }

  private Artifact createArtifactWithDummyFile(String coordinates) throws URISyntaxException {
    return new DefaultArtifact(coordinates)
        .setFile(Paths.get(URLClassLoader.getSystemResource("dummy-0.0.1.jar").toURI()).toFile());
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
    mockMojoExecution = mock(MojoExecution.class);
    when(mockMojoExecution.getLifecyclePhase()).thenReturn("verify");
    when(mockRuleHelper.evaluate("${mojoExecution}")).thenReturn(mockMojoExecution);
    org.apache.maven.artifact.DefaultArtifact rootArtifact =
        new org.apache.maven.artifact.DefaultArtifact(
            "com.google.cloud",
            "linkage-checker-rule-test",
            "0.0.1",
            "compile",
            "jar",
            null,
            new DefaultArtifactHandler());
    rootArtifact.setFile(new File("dummy.jar"));
    when(mockProject.getArtifact()).thenReturn(rootArtifact);
  }

  /**
   * Returns a dependency graph node resolved from {@code coordinates}.
   */
  private DependencyNode createResolvedDependencyGraph(String... coordinates)
      throws RepositoryException {
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRootArtifact(dummyArtifactWithFile);
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

  private void setupMockDependencyResolution(String... coordinates) throws RepositoryException {
    // The root node is Maven artifact "a:b:0.1" that has dependencies specified as `coordinates`.
    DependencyNode rootNode = createResolvedDependencyGraph(coordinates);
    Traverser<DependencyNode> traverser = Traverser.forGraph(node -> node.getChildren());

    // DependencyResolutionResult.getDependencies returns depth-first order
    ImmutableList<Dependency> dummyDependencies =
        ImmutableList.copyOf(traverser.depthFirstPreOrder(rootNode)).stream()
            .map(DependencyNode::getDependency)
            .filter(Objects::nonNull)
            .collect(toImmutableList());
    when(mockDependencyResolutionResult.getDependencies()).thenReturn(dummyDependencies);
    when(mockDependencyResolutionResult.getResolvedDependencies())
        .thenReturn(
            ImmutableList.copyOf(traverser.breadthFirst(rootNode.getChildren())).stream()
                .map(DependencyNode::getDependency)
                .filter(Objects::nonNull)
                .collect(toImmutableList()));
    when(mockDependencyResolutionResult.getDependencyGraph()).thenReturn(rootNode);
    when(mockProject.getDependencies())
        .thenReturn(
            dummyDependencies.subList(0, coordinates.length).stream()
                .map(LinkageCheckerRuleTest::toDependency)
                .collect(Collectors.toList()));
  }

  @Test
  public void testExecute_shouldPassGoodProject()
      throws EnforcerRuleException, RepositoryException {
    // Since Guava 27, it requires com.google.guava:failureaccess artifact in its dependency.
    setupMockDependencyResolution("com.google.guava:guava:27.0.1-jre");
    // This should not raise an EnforcerRuleException
    rule.execute(mockRuleHelper);
    verify(mockLog).info("No error found");
  }

  @Test
  public void testExecute_shouldPassGoodProject_sessionProperties()
      throws EnforcerRuleException, RepositoryException, DependencyResolutionException {
    setupMockDependencyResolution("com.google.guava:guava:27.0.1-jre");

    rule.execute(mockRuleHelper);

    ArgumentCaptor<DependencyResolutionRequest> argumentCaptor =
        ArgumentCaptor.forClass(DependencyResolutionRequest.class);
    verify(mockProjectDependenciesResolver).resolve(argumentCaptor.capture());
    Map<String, String> propertiesUsedInSession =
        argumentCaptor.getValue().getRepositorySession().getSystemProperties();
    Truth.assertWithMessage(
            "RepositorySystemSession should have variables such as os.detected.classifier")
        .that(propertiesUsedInSession)
        .containsAtLeastEntriesIn(OsProperties.detectOsProperties());
    // There was a problem in resolving profiles because original properties were missing (#817)
    Truth.assertWithMessage("RepositorySystemSession should have original properties")
        .that(propertiesUsedInSession)
        .containsAtLeastEntriesIn(repositorySystemSession.getSystemProperties());
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
      ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);
      verify(mockLog, times(2)).error(errorMessageCaptor.capture());

      List<String> errorMessages = errorMessageCaptor.getAllValues();
      Truth.assertThat(errorMessages.get(0)).startsWith("Linkage Checker rule found 112 errors.");
      Truth.assertThat(errorMessages.get(1))
          .startsWith(
              "Problematic artifacts in the dependency tree:\n"
                  + "com.google.appengine:appengine-api-1.0-sdk:1.9.64 is at:\n"
                  + "  a:b:jar:0.1 / com.google.appengine:appengine-api-1.0-sdk:1.9.64 (compile)");
      assertEquals("Failed while checking class path. See above error report.", ex.getMessage());
    }
  }

  @Test
  public void testExecute_shouldFailForBadProject_reachableErrors() throws RepositoryException {
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
      throws RepositoryException, EnforcerRuleException {
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
      throws EnforcerRuleException, RepositoryException {
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
  public void testExecute_shouldSkipBadBomWithNonPomPackaging() throws EnforcerRuleException {
    rule.setDependencySection(DependencySection.DEPENDENCY_MANAGEMENT);
    setupMockDependencyManagementSection(
        "com.google.api-client:google-api-client:1.27.0", "io.grpc:grpc-core:1.17.1");
    when(mockProject.getArtifact())
        .thenReturn(
            new org.apache.maven.artifact.DefaultArtifact(
                "com.google.cloud",
                "linkage-checker-rule-test-bom",
                "0.0.1",
                "compile",
                "jar", // BOM should have pom here
                null,
                new DefaultArtifactHandler()));
    rule.execute(mockRuleHelper);
  }

  @Test
  public void testExecute_shouldSkipNonBomPom() throws EnforcerRuleException {
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
    // No exception
    rule.execute(mockRuleHelper);
  }

  @Test
  public void testExecute_shouldExcludeTestScope() throws EnforcerRuleException {
    org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
    Artifact artifact = new DefaultArtifact("junit:junit:3.8.2");
    dependency.setArtifactId(artifact.getArtifactId());
    dependency.setGroupId(artifact.getGroupId());
    dependency.setVersion(artifact.getVersion());
    dependency.setClassifier(artifact.getClassifier());
    dependency.setScope("test");

    when(mockDependencyResolutionResult.getDependencyGraph()).thenReturn(
        new DefaultDependencyNode(dummyArtifactWithFile)
    );
    when(mockProject.getDependencies())
        .thenReturn(ImmutableList.of(dependency));

    rule.execute(mockRuleHelper);
  }

  @Test
  public void testExecute_shouldFailForBadProjectWithBundlePackaging() throws RepositoryException {
    try {
      // This artifact is known to contain classes missing dependencies
      setupMockDependencyResolution("com.google.appengine:appengine-api-1.0-sdk:1.9.64");

      org.apache.maven.artifact.DefaultArtifact rootArtifact =
          new org.apache.maven.artifact.DefaultArtifact(
              "com.google.cloud",
              "linkage-checker-rule-test",
              "0.0.1",
              "compile",
              "bundle", // Maven Bundle Plugin uses "bundle" packaging.
              null,
              new DefaultArtifactHandler());
      rootArtifact.setFile(new File("dummy.jar"));
      when(mockProject.getArtifact()).thenReturn(rootArtifact);

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
  public void testExecute_shouldFilterExclusionRule()
      throws RepositoryException, URISyntaxException {
    try {
      // This artifact is known to contain classes missing dependencies
      setupMockDependencyResolution("com.google.appengine:appengine-api-1.0-sdk:1.9.64");
      String exclusionFileLocation =
          Paths.get(ClassLoader.getSystemResource("appengine-exclusion.xml").toURI())
              .toAbsolutePath()
              .toString();
      rule.setExclusionFile(exclusionFileLocation);
      rule.execute(mockRuleHelper);
      Assert.fail(
          "The rule should raise an EnforcerRuleException for artifacts missing dependencies");
    } catch (EnforcerRuleException ex) {
      // pass.
      // The number of errors was 112 in testExecute_shouldFailForBadProjectWithBundlePackaging
      verify(mockLog).error(ArgumentMatchers.startsWith("Linkage Checker rule found 93 errors."));
      assertEquals("Failed while checking class path. See above error report.", ex.getMessage());
    }
  }

  private DependencyResolutionException createDummyResolutionException(
      Artifact missingArtifact, DependencyResolutionResult resolutionResult) {
    Throwable cause3 = new ArtifactNotFoundException(missingArtifact, null);
    Throwable cause2 = new ArtifactResolutionException(null, "dummy 3", cause3);
    Throwable cause1 = new DependencyResolutionException(resolutionResult, "dummy 2", cause2);
    DependencyResolutionException exception =
        new DependencyResolutionException(resolutionResult, "dummy 1", cause1);
    return exception;
  }

  @Test
  public void testArtifactTransferError()
      throws RepositoryException, DependencyResolutionException {
    DependencyNode graph = createResolvedDependencyGraph("org.apache.maven:maven-core:jar:3.5.2");
    DependencyResolutionResult resolutionResult = mock(DependencyResolutionResult.class);
    when(resolutionResult.getDependencyGraph()).thenReturn(graph);
    DependencyResolutionException exception =
        createDummyResolutionException(
            new DefaultArtifact("aopalliance:aopalliance:1.0"), resolutionResult);
    when(mockProjectDependenciesResolver.resolve(any())).thenThrow(exception);

    try {
      rule.execute(mockRuleHelper);
      fail("The rule should throw EnforcerRuleException upon dependency resolution exception");
    } catch (EnforcerRuleException expected) {
      verify(mockLog)
          .warn(
              "aopalliance:aopalliance:jar:1.0 was not resolved. "
                  + "Dependency path: a:b:jar:0.1 > "
                  + "org.apache.maven:maven-core:jar:3.5.2 (compile) > "
                  + "com.google.inject:guice:jar:no_aop:4.0 (compile) > "
                  + "aopalliance:aopalliance:jar:1.0 (compile)");
    }
  }

  @Test
  public void testArtifactTransferError_acceptableMissingArtifact()
      throws URISyntaxException, DependencyResolutionException, EnforcerRuleException {
    // Creating a dummy tree
    //   com.google.foo:project
    //     +- com.google.foo:child1 (provided)
    //        +- com.google.foo:child2 (optional)
    //           +- xerces:xerces-impl:jar:2.6.2 (optional)
    DefaultDependencyNode missingArtifactNode =
        new DefaultDependencyNode(
            new Dependency(
                createArtifactWithDummyFile("xerces:xerces-impl:jar:2.6.2"), "compile", true));
    DefaultDependencyNode child2 =
        new DefaultDependencyNode(
            new Dependency(
                createArtifactWithDummyFile("com.google.foo:child2:1.0.0"), "compile", true));
    child2.setChildren(ImmutableList.of(missingArtifactNode));
    DefaultDependencyNode child1 =
        new DefaultDependencyNode(
            new Dependency(createArtifactWithDummyFile("com.google.foo:child1:1.0.0"), "provided"));
    child1.setChildren(ImmutableList.of(child2));
    DefaultDependencyNode root =
        new DefaultDependencyNode(createArtifactWithDummyFile("com.google.foo:project:1.0.0"));
    root.setChildren(ImmutableList.of(child1));

    DependencyResolutionResult resolutionResult = mock(DependencyResolutionResult.class);
    when(resolutionResult.getDependencyGraph()).thenReturn(root);
    when(resolutionResult.getResolvedDependencies())
        .thenReturn(
            ImmutableList.of(
                child1.getDependency(),
                child2.getDependency(),
                missingArtifactNode.getDependency()));

    // xerces-impl does not exist in Maven Central
    DependencyResolutionException exception =
        createDummyResolutionException(missingArtifactNode.getArtifact(), resolutionResult);

    when(mockProjectDependenciesResolver.resolve(any())).thenThrow(exception);

    // Should not throw DependencyResolutionException, because the missing xerces-impl is under both
    // provided and optional dependencies.
    rule.execute(mockRuleHelper);
  }

  @Test
  public void testArtifactTransferError_missingArtifactNotInGraph()
      throws URISyntaxException, DependencyResolutionException, EnforcerRuleException {
    // Creating a dummy tree
    //   com.google.foo:project
    //     +- com.google.foo:child1 (provided)
    //        +- com.google.foo:child2 (optional)
    DefaultDependencyNode child2 =
        new DefaultDependencyNode(
            new Dependency(
                createArtifactWithDummyFile("com.google.foo:child2:1.0.0"), "compile", true));
    DefaultDependencyNode child1 =
        new DefaultDependencyNode(
            new Dependency(createArtifactWithDummyFile("com.google.foo:child1:1.0.0"), "provided"));
    child1.setChildren(ImmutableList.of(child2));
    DefaultDependencyNode root =
        new DefaultDependencyNode(createArtifactWithDummyFile("com.google.foo:project:1.0.0"));
    root.setChildren(ImmutableList.of(child1));

    DependencyResolutionResult resolutionResult = mock(DependencyResolutionResult.class);
    when(resolutionResult.getDependencyGraph()).thenReturn(root);
    when(resolutionResult.getResolvedDependencies())
        .thenReturn(ImmutableList.of(child1.getDependency(), child2.getDependency()));

    // The exception is caused by this node but this node does not appear in the dependency graph.
    DefaultDependencyNode missingArtifactNode =
        new DefaultDependencyNode(
            new Dependency(
                createArtifactWithDummyFile("xerces:xerces-impl:jar:2.6.2"), "compile", true));
    // xerces-impl does not exist in Maven Central
    DependencyResolutionException exception =
        createDummyResolutionException(missingArtifactNode.getArtifact(), resolutionResult);

    when(mockProjectDependenciesResolver.resolve(any())).thenThrow(exception);

    rule.execute(mockRuleHelper);
    verify(mockLog)
        .warn("xerces:xerces-impl:jar:2.6.2 was not resolved. Dependency path is unknown.");
  }

  @Test
  public void testSkippingProjectWithoutFile() throws EnforcerRuleException {
    when(mockProject.getArtifact())
        .thenReturn(
            new org.apache.maven.artifact.DefaultArtifact(
                "com.google.cloud",
                "foo-tests",
                "0.0.1",
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler()));
    rule.execute(mockRuleHelper);
  }

  @Test
  public void testValidatePhase() {
    when(mockProject.getArtifact())
        .thenReturn(
            new org.apache.maven.artifact.DefaultArtifact(
                "com.google.cloud",
                "foo-tests",
                "0.0.1",
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler()));

    when(mockMojoExecution.getLifecyclePhase()).thenReturn("validate");
    try {
      rule.execute(mockRuleHelper);
      fail("The rule should throw EnforcerRuleException when running in validate phase");
    } catch (EnforcerRuleException ex) {
      assertEquals(
          "To run the check on the compiled class files, the linkage checker enforcer rule should"
              + " be bound to the 'verify' phase. Current phase: validate",
          ex.getMessage());
    }
  }
}
