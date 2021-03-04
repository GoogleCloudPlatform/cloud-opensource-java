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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;

/**
 * Aether initialization. This is based on Apache Maven Resolver 1.4.2 or later.
 * There are many other versions of Aether from Sonatype and the Eclipse
 * Project, but this is the current one.
 */
public final class RepositoryUtility {
  
  public static final RemoteRepository CENTRAL =
      new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();

  // DefaultTransporterProvider.newTransporter checks these transporters
  private static final ImmutableSet<String> ALLOWED_REPOSITORY_URL_SCHEMES =
      ImmutableSet.of("file", "http", "https");

  private RepositoryUtility() {}

  /**
   * Creates a new system configured for file and HTTP repository resolution.
   */
  public static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
  
    return locator.getService(RepositorySystem.class);
  }

  @VisibleForTesting
  static DefaultRepositorySystemSession createDefaultRepositorySystemSession(
      RepositorySystem system) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepository = new LocalRepository(findLocalRepository());
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepository));
    return session;
  }

  /**
   * Opens a new Maven repository session that looks for the local repository in the
   * customary ~/.m2 directory. If not found, it creates an initially empty repository in
   * a temporary location.
   */
  public static DefaultRepositorySystemSession newSession(RepositorySystem system) {
    DefaultRepositorySystemSession session = createDefaultRepositorySystemSession(system);
    return session;
  }

  /**
   * Opens a new Maven repository session that generates the same dependency graph as {@link
   * org.apache.maven.project.ProjectDependenciesResolver} generates when it compiles a Maven
   * project. The dependency graph has the following attributes:
   *
   * <ul>
   *   <li>Direct optional dependencies are included. Other optional dependencies are omitted.
   *   <li>Direct provided-scope dependencies are included. Other provided-scope dependencies are
   *       omitted.
   * </ul>
   */
  public static DefaultRepositorySystemSession newSessionForMaven(RepositorySystem system) {
    DefaultRepositorySystemSession session = createDefaultRepositorySystemSession(system);
    DependencySelector dependencySelector =
        new AndDependencySelector(
            new ScopeDependencySelector("test"),
            new OptionalDependencySelector(),
            new DirectProvidedDependencySelector(),
            new ExclusionDependencySelector(),
            new FilteringZipDependencySelector());
    return session.setDependencySelector(dependencySelector);
  }

  /**
   * Open a new Maven repository session for full dependency graph resolution.
   *
   * @see {@link DependencyGraphBuilder}
   */
  static DefaultRepositorySystemSession newSessionForFullDependency(RepositorySystem system) {
    // This combination of DependencySelector comes from the default specified in
    // `MavenRepositorySystemUtils.newSession`.
    // LinkageChecker needs to include 'provided'-scope and optional dependencies.
    DependencySelector dependencySelector =
        new AndDependencySelector(
            // ScopeDependencySelector takes exclusions. 'Provided' scope is not here to avoid
            // false positive in LinkageChecker.
            new ScopeDependencySelector("test"),
            new ExclusionDependencySelector(),
            new FilteringZipDependencySelector());
    
    return newSession(system, dependencySelector);
  }

  private static DefaultRepositorySystemSession newSession(
      RepositorySystem system, DependencySelector dependencySelector) {
    DefaultRepositorySystemSession session = createDefaultRepositorySystemSession(system);
    session.setDependencySelector(dependencySelector);

    // By default, Maven's MavenRepositorySystemUtils.newSession() returns a session with
    // ChainedDependencyGraphTransformer(ConflictResolver(...), JavaDependencyContextRefiner()).
    // Because the full dependency graph does not resolve conflicts of versions, this session does
    // not use ConflictResolver.
    session.setDependencyGraphTransformer(
        new ChainedDependencyGraphTransformer(
            new CycleBreakerGraphTransformer(), // Avoids StackOverflowError
            new JavaDependencyContextRefiner()));

    // No dependency management in the full dependency graph
    session.setDependencyManager(null);

    return session;
  }
  
  static DefaultRepositorySystemSession newSessionForVerboseDependency(RepositorySystem system) {
    DependencySelector dependencySelector =
        new AndDependencySelector(
            // ScopeDependencySelector takes exclusions. 'Provided' scope is not here to avoid
            // false positive in LinkageChecker.
            new ScopeDependencySelector("test"),
            new OptionalDependencySelector(),
            new ExclusionDependencySelector(),
            new FilteringZipDependencySelector());
    
    return newSession(system, dependencySelector);
  }
  
  static DefaultRepositorySystemSession newSessionForVerboseListDependency(
      RepositorySystem system) {
    DependencySelector dependencySelector =
        new AndDependencySelector(
            // ScopeDependencySelector takes exclusions. 'Provided' scope is not here to avoid
            // false positive in LinkageChecker.
            new ScopeDependencySelector("test"),
            new BanOptionalDependencySelector(),
            new ExclusionDependencySelector(),
            new FilteringZipDependencySelector());

    return newSession(system, dependencySelector);
  }


  private static String findLocalRepository() {
    // TODO is there Maven code for this?
    Path home = Paths.get(System.getProperty("user.home"));
    Path localRepo = home.resolve(".m2").resolve("repository");
    if (Files.isDirectory(localRepo)) {
      return localRepo.toAbsolutePath().toString();
    } else {
      return makeTemporaryLocalRepository(); 
   }
  }

  private static String makeTemporaryLocalRepository() {
    try {
      File temporaryDirectory = Files.createTempDirectory("m2").toFile();
      temporaryDirectory.deleteOnExit();
      return temporaryDirectory.getAbsolutePath();
    } catch (IOException ex) {
      return null;
    }
  }

  static MavenProject createMavenProject(Path pomFile, RepositorySystemSession session)
      throws MavenRepositoryException {
    // MavenCli's way to instantiate PlexusContainer
    ClassWorld classWorld =
        new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
    ContainerConfiguration containerConfiguration =
        new DefaultContainerConfiguration()
            .setClassWorld(classWorld)
            .setRealm(classWorld.getClassRealm("plexus.core"))
            .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
            .setAutoWiring(true)
            .setJSR250Lifecycle(true)
            .setName("linkage-checker");
    try {
      PlexusContainer container = new DefaultPlexusContainer(containerConfiguration);

      MavenExecutionRequest mavenExecutionRequest = new DefaultMavenExecutionRequest();
      ProjectBuildingRequest projectBuildingRequest =
          mavenExecutionRequest.getProjectBuildingRequest();

      projectBuildingRequest.setRepositorySession(session);

      // Profile activation needs properties such as JDK version
      Properties properties = new Properties(); // allowing duplicate entries
      properties.putAll(projectBuildingRequest.getSystemProperties());
      properties.putAll(OsProperties.detectOsProperties());
      properties.putAll(System.getProperties());
      projectBuildingRequest.setSystemProperties(properties);

      ProjectBuilder projectBuilder = container.lookup(ProjectBuilder.class);
      ProjectBuildingResult projectBuildingResult =
          projectBuilder.build(pomFile.toFile(), projectBuildingRequest);
      return projectBuildingResult.getProject();
    } catch (PlexusContainerException | ComponentLookupException | ProjectBuildingException ex) {
      throw new MavenRepositoryException(ex);
    }
  }

  /**
   * Returns Maven repository specified as {@code mavenRepositoryUrl}, after validating the syntax
   * of the URL.
   *
   * @throws IllegalArgumentException if the URL is malformed for a Maven repository
   */
  public static RemoteRepository mavenRepositoryFromUrl(String mavenRepositoryUrl) {
    try {
      // Because the protocol is not an empty string (checked below), this URI is absolute.
      new URI(checkNotNull(mavenRepositoryUrl));
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Invalid URL syntax: " + mavenRepositoryUrl);
    }

    RemoteRepository repository =
        new RemoteRepository.Builder(mavenRepositoryUrl, "default", mavenRepositoryUrl).build();

    checkArgument(
        ALLOWED_REPOSITORY_URL_SCHEMES.contains(repository.getProtocol()),
        "Scheme: '%s' is not in %s",
        repository.getProtocol(),
        ALLOWED_REPOSITORY_URL_SCHEMES);
    return repository;
  }

  private static VersionRangeResult findVersionRange(
      RepositorySystem repositorySystem,
      RepositorySystemSession session,
      String groupId,
      String artifactId)
      throws MavenRepositoryException {

    Artifact artifactWithVersionRange = new DefaultArtifact(groupId, artifactId, null, "(,]");
    VersionRangeRequest request =
        new VersionRangeRequest(
            artifactWithVersionRange, ImmutableList.of(RepositoryUtility.CENTRAL), null);

    try {
      return repositorySystem.resolveVersionRange(session, request);
    } catch (VersionRangeResolutionException ex) {
      throw new MavenRepositoryException(ex);
    }
  }

  /** Returns the highest version for {@code groupId:artifactId} in {@code repositorySystem}. */
  @VisibleForTesting
  static String findHighestVersion(
      RepositorySystem repositorySystem,
      RepositorySystemSession session,
      String groupId,
      String artifactId)
      throws MavenRepositoryException {
    return findVersionRange(repositorySystem, session, groupId, artifactId)
        .getHighestVersion()
        .toString();
  }

  /**
   * Returns list of versions available for {@code groupId:artifactId} in {@code repositorySystem}.
   * The returned list is in ascending order with regard to {@link
   * org.eclipse.aether.util.version.GenericVersionScheme}. The highest version comes last.
   */
  public static ImmutableList<String> findVersions(
      RepositorySystem repositorySystem, String groupId, String artifactId)
      throws MavenRepositoryException {
    RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);
    // getVersions returns a list in ascending order
    return findVersionRange(repositorySystem, session, groupId, artifactId).getVersions().stream()
        .map(version -> version.toString())
        .collect(toImmutableList());
  }

  /**
   * Returns the latest Maven coordinates for {@code groupId:artifactId} in
   * {@code repositorySystem}.
   */
  public static String findLatestCoordinates(
      RepositorySystem repositorySystem, String groupId, String artifactId)
      throws MavenRepositoryException {
    RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);
    String highestVersion = findHighestVersion(repositorySystem, session, groupId, artifactId);
    return String.format("%s:%s:%s", groupId, artifactId, highestVersion);
  }

}
