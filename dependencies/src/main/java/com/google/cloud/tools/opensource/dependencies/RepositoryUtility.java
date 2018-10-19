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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
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

/**
 * Aether initialization.
 */
public final class RepositoryUtility {
  
  public static final RemoteRepository CENTRAL =
      new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();

  private RepositoryUtility() {}

  /**
   * Create a new system configured for file and HTTP repository resolution.
   */
  public static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
  
    return locator.getService(RepositorySystem.class);
  }

  /**
   * Open a new Maven repository session that looks for the local repository in the
   * customary ~/.m2 directory. If not found, it creates an initially empty repository in
   * a temporary location.
   */
  public static DefaultRepositorySystemSession newSession(RepositorySystem system ) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
  
    LocalRepository localRepository = new LocalRepository(findLocalRepository().getAbsolutePath());
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepository));

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
    request.addRepository(RepositoryUtility.CENTRAL);
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
        System.err.println("Duplicate dependency " + dependency);
      }
    }
    return managedDependencies;
  }

}
