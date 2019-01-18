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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;

/**
 * Aether initialization.
 */
public final class RepositoryUtility {

  private static final Logger logger = Logger.getLogger(RepositoryUtility.class.getName());
  
  public static final RemoteRepository CENTRAL =
      new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();

  private static ImmutableList<RemoteRepository> mavenRepositories = ImmutableList.of(CENTRAL);

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

  private static DefaultRepositorySystemSession createDefaultRepositorySystemSession(
      RepositorySystem system) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepository = new LocalRepository(findLocalRepository().getAbsolutePath());
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepository));
    return session;
  }

  /**
   * Opens a new Maven repository session that looks for the local repository in the
   * customary ~/.m2 directory. If not found, it creates an initially empty repository in
   * a temporary location.
   */
  static RepositorySystemSession newSession(RepositorySystem system) {
    DefaultRepositorySystemSession session = createDefaultRepositorySystemSession(system);
    session.setReadOnly();
    return session;
  }

  /**
   * Opens a new Maven repository session in the same way as {@link
   * RepositoryUtility#newSession(RepositorySystem)}, with its dependency selector to include
   * dependencies with 'provided' scope.
   */
  static RepositorySystemSession newSessionWithProvidedScope(RepositorySystem system) {
    DefaultRepositorySystemSession session = createDefaultRepositorySystemSession(system);

    // To exclude log4j-api-java9:zip:2.11.1, which is not published.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/339
    DependencySelector filteringZipDependencySelector =
        new DependencySelector() {

          @Override
          public boolean selectDependency(Dependency dependency) {
            Artifact artifact = dependency.getArtifact();
            Map<String, String> properties = artifact.getProperties();
            // Because StaticLinkageChecker only checks jar file, zip files are not needed
            logger.fine("Skipping an artifact with type:zip: " + artifact);
            return !"zip".equals(properties.get("type"));
          }

          @Override
          public DependencySelector deriveChildSelector(
              DependencyCollectionContext dependencyCollectionContext) {
            return this;
          }
        };

    // This combination of DependencySelector comes from the default specified in
    // `MavenRepositorySystemUtils.newSession`.
    // StaticLinkageChecker needs to include 'provided' scope.
    DependencySelector dependencySelector =
        new AndDependencySelector(
            // ScopeDependencySelector takes exclusions. 'Provided' scope is not here to avoid
            // false positive in StaticLinkageChecker.
            new ScopeDependencySelector("test"),
            new OptionalDependencySelector(),
            new ExclusionDependencySelector(),
            filteringZipDependencySelector);
    session.setDependencySelector(dependencySelector);
    session.setReadOnly();

    return session;
  }

  private static File findLocalRepository() {
    Path home = Paths.get(System.getProperty("user.home"));
    Path localRepo = home.resolve(".m2").resolve("repository");
    if (Files.isDirectory(localRepo)) {
      return localRepo.toFile();
    } else {
      File temporaryDirectory = com.google.common.io.Files.createTempDir();
      temporaryDirectory.deleteOnExit();
      return temporaryDirectory; 
   }
  }

  /**
   * Parse the dependencyManagement section of an artifact and return the
   * artifacts included there.
   */
  // TODO Consider the possibility that the artifact is not a BOM; 
  // that is, that it does not have a dependency management section.
  public static List<Artifact> readBom(Artifact artifact) throws ArtifactDescriptorException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);

    ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();

    for (RemoteRepository repository : mavenRepositories) {
      request.addRepository(repository);
    }
    request.setArtifact(artifact);

    ArtifactDescriptorResult resolved = system.readArtifactDescriptor(session, request);
    List<Exception> exceptions = resolved.getExceptions();
    if (!exceptions.isEmpty()) {
      throw new ArtifactDescriptorException(resolved, exceptions.get(0).getMessage());
    }
    
    List<Artifact> managedDependencies = new ArrayList<>();
    for (Dependency dependency : resolved.getManagedDependencies()) {
      Artifact managed = dependency.getArtifact();
      if ("testlib".equals(managed.getClassifier())) {
        // we don't report on test libraries
        continue;
      }
      
      String type = managed.getProperty(ArtifactProperties.TYPE, "jar");
      if ("test-jar".equals(type)) {
        continue;
      }
      
      // TODO remove this hack once we get these out of 
      // google-cloud-java's BOM
      if (managed.getArtifactId().equals("google-cloud-logging-logback")
          || managed.getArtifactId().equals("google-cloud-contrib")) {
        continue;
      }
      if (!managedDependencies.contains(managed)) {
        managedDependencies.add(managed);
      } else {
        logger.severe("Duplicate dependency " + dependency);
      }
    }
    return managedDependencies;
  }

  /**
   * Sets {@link #mavenRepositories} to search for dependencies.
   *
   * @param addMavenCentral if true, add Maven Central to the end of the repository list
   * @throws IllegalArgumentException if a URL is malformed or not having an allowed scheme
   */
  public static void setRepositories(
      Iterable<String> mavenRepositoryUrls, boolean addMavenCentral) {
    ImmutableList.Builder<RemoteRepository> repositoryListBuilder = ImmutableList.builder();
    for (String mavenRepositoryUrl : mavenRepositoryUrls) {
      try {
        // Because the protocol is not an empty string, this URI is absolute.
        new URI(mavenRepositoryUrl);
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

      repositoryListBuilder.add(repository);
    }

    if (addMavenCentral) {
      repositoryListBuilder.add(CENTRAL);
    }

    mavenRepositories = repositoryListBuilder.build();
  }

  /** Adds {@link #mavenRepositories} to {@code collectRequest}. */
  public static void addRepositoriesToRequest(CollectRequest collectRequest) {
    for (RemoteRepository repository : mavenRepositories) {
      collectRequest.addRepository(repository);
    }
  }
}
