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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.SettingsReader;
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
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;

/**
 * Aether initialization.
 */
public final class RepositoryUtility {

  private static final Logger logger = Logger.getLogger(RepositoryUtility.class.getName());
  
  public static final RemoteRepository CENTRAL =
      new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();

  public static final RemoteRepository OFFLINE =
      new RemoteRepository.Builder("offline", "default", "file://no-such-file/maven/central").build();

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
   * Opens a new Maven repository session using the settings.xml file.
   */
  public static Bom readBomWithSettings(String coordinates, File settingsFile) {

    ClassWorld classWorld =
        new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
    ContainerConfiguration containerConfiguration =
        new DefaultContainerConfiguration()
            .setClassWorld(classWorld)
            .setRealm(classWorld.getClassRealm("plexus.core"))
            .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
            .setAutoWiring(true)
            .setJSR250Lifecycle(true)
            .setName("repository-utility");
    try {
      PlexusContainer container = new DefaultPlexusContainer(containerConfiguration);
      MavenExecutionRequest mavenRequest = new DefaultMavenExecutionRequest();


      MavenRepositorySystem mavenRepositorySystem = container.lookup(MavenRepositorySystem.class);

      // Maven Central
      ArtifactRepository repo = mavenRepositorySystem.createDefaultRemoteRepository(mavenRequest);
      mavenRequest.addRemoteRepository(repo);

      // Settings.xml
      SettingsReader reader = container.lookup(SettingsReader.class);
      MavenExecutionRequestPopulator requestPopulator = container.lookup(MavenExecutionRequestPopulator.class);
      mavenRequest.setUserSettingsFile(settingsFile);
      Settings userSettings = reader.read(settingsFile, null);
      requestPopulator.populateFromSettings(mavenRequest, userSettings);

      mavenRequest.setLocalRepositoryPath(Paths.get("test_local_repo").toFile());

      org.apache.maven.repository.RepositorySystem repositorySystem = container.lookup(org.apache.maven.repository.RepositorySystem.class);
      ArtifactRepository localRepo = repositorySystem.createLocalRepository(mavenRequest.getLocalRepositoryPath());
      mavenRequest.setLocalRepository(localRepo);

      DefaultRepositorySystemSessionFactory repositorySessionFactory = container.lookup(
          DefaultRepositorySystemSessionFactory.class);

      // This internally calls mavenRepositorySystem.injectMirror(
      RepositorySystemSession session = repositorySessionFactory.newRepositorySession(mavenRequest);

      ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();

      // This is never updated by session. If this is omitted, then readArtifactDescriptor does not
      // read anything.
      // request.addRepository(OFFLINE);

      request.setArtifact(new DefaultArtifact(coordinates));

      // This ignored settings.xml file
      // RepositorySystem aetherRepositorySystem = newRepositorySystem();
      RepositorySystem aetherRepositorySystem = container.lookup(RepositorySystem.class);


      // This has mirror correctly
      List<Mirror> mirrors = mavenRequest.getMirrors();

      // This has mirrors correctly
      MirrorSelector mirrorSelector = session.getMirrorSelector();

      // This does not seem to use the mirrors defined in settings.xml
      ArtifactDescriptorResult resolved = aetherRepositorySystem.readArtifactDescriptor(session, request);

      Bom bom = Bom.create(resolved);
      return bom;
    } catch (Exception ex) {
      throw new RuntimeException("failed to read BOM", ex);
    }
  }


  /**
   * Open a new Maven repository session for full dependency graph resolution.
   *
   * @see {@link DependencyGraphBuilder}
   */
  static DefaultRepositorySystemSession newSessionForFullDependency(RepositorySystem system) {
    DefaultRepositorySystemSession session = createDefaultRepositorySystemSession(system);

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

  // TODO arguably this now belongs in the BOM class
  public static Bom readBom(Path pomFile) throws MavenRepositoryException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);

    MavenProject mavenProject = createMavenProject(pomFile, session);
    String coordinates = mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() 
        + ":" + mavenProject.getVersion();
    DependencyManagement dependencyManagement = mavenProject.getDependencyManagement();
    List<org.apache.maven.model.Dependency> dependencies = dependencyManagement.getDependencies();

    ArtifactTypeRegistry registry = session.getArtifactTypeRegistry();
    ImmutableList<Artifact> artifacts = dependencies.stream()
        .map(dependency -> RepositoryUtils.toDependency(dependency, registry))
        .map(Dependency::getArtifact)
        .filter(artifact -> !shouldSkipBomMember(artifact))
        .collect(toImmutableList());
    
    Bom bom = new Bom(coordinates, artifacts);
    return bom;
  }

  private static MavenProject createMavenProject(Path pomFile, RepositorySystemSession session)
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
   * Parse the dependencyManagement section of an artifact and return the artifacts included there.
   */
  public static Bom readBom(String coordinates) throws ArtifactDescriptorException {
    return readBom(coordinates, ImmutableList.of(CENTRAL, OFFLINE));
  }

  /**
   * Parse the dependencyManagement section of an artifact and return the artifacts included there.
   */
  public static Bom readBom(String coordinates, List<RemoteRepository> remoteRepositories)
      throws ArtifactDescriptorException {
    Artifact artifact = new DefaultArtifact(coordinates);

    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);

    ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();

    for (RemoteRepository repository : remoteRepositories) {
      request.addRepository(repository);
    }

    request.setArtifact(artifact);

    ArtifactDescriptorResult resolved = system.readArtifactDescriptor(session, request);
    Bom bom = Bom.create(resolved);
    return bom;
  }

  private static final ImmutableSet<String> BOM_SKIP_ARTIFACT_IDS =
      ImmutableSet.of("google-cloud-logging-logback", "google-cloud-contrib");

  /** Returns true if the {@code artifact} in BOM should be skipped for checks. */
  public static boolean shouldSkipBomMember(Artifact artifact) {
    if ("testlib".equals(artifact.getClassifier())) {
      // we don't report on test libraries
      return true;
    }

    String type = artifact.getProperty(ArtifactProperties.TYPE, "jar");
    if ("test-jar".equals(type)) {
      return true;
    }

    // TODO remove this hack once we get these out of google-cloud-java's BOM
    if (BOM_SKIP_ARTIFACT_IDS.contains(artifact.getArtifactId())) {
      return true;
    }

    return false;
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
        new RemoteRepository.Builder(null, "default", mavenRepositoryUrl).build();

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

    Artifact artifactWithVersionRange = new DefaultArtifact(groupId, artifactId, null, "(0,]");
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
   * org.eclipse.aether.util.version.GenericVersionScheme}; the highest version comes at last.
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
   * Returns the latest Maven coordinates for {@code groupId:artifactId} in {@code
   * repositorySystem}.
   */
  public static String findLatestCoordinates(
      RepositorySystem repositorySystem, String groupId, String artifactId)
      throws MavenRepositoryException {
    RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);
    String highestVersion = findHighestVersion(repositorySystem, session, groupId, artifactId);
    return String.format("%s:%s:%s", groupId, artifactId, highestVersion);
  }
}
