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

package com.google.cloud.tools.opensource.dependencies;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

public final class Bom {

  private final ImmutableList<Artifact> artifacts;
  private final String coordinates;

  /**
   * @param coordinates group:artifact:version
   * @param artifacts the artifacts found in this BOM's managedDependencies section
   */
  @VisibleForTesting
  public Bom(String coordinates, ImmutableList<Artifact> artifacts) {
    this.coordinates = Preconditions.checkNotNull(coordinates);
    if (artifacts == null) {
      artifacts = ImmutableList.of();
    }
    this.artifacts = artifacts;
  }

  /**
   * Returns the artifacts found in this BOM's managedDependencies section.
   */
  public ImmutableList<Artifact> getManagedDependencies() {
    return artifacts;
  }

  /**
   * Returns group:artifact:version.
   */
  public String getCoordinates() {
    return coordinates;
  }

  public static Bom readBom(Path pomFile) throws MavenRepositoryException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);
  
    MavenProject mavenProject = RepositoryUtility.createMavenProject(pomFile, session);
    String coordinates = mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() 
        + ":" + mavenProject.getVersion();
    DependencyManagement dependencyManagement = mavenProject.getDependencyManagement();
    List<org.apache.maven.model.Dependency> dependencies = dependencyManagement.getDependencies();
  
    ArtifactTypeRegistry registry = session.getArtifactTypeRegistry();
    ImmutableList<Artifact> artifacts = dependencies.stream()
        .map(dependency -> RepositoryUtils.toDependency(dependency, registry))
        .map(Dependency::getArtifact)
        .filter(artifact -> !shouldSkipBomMember(artifact))
        .collect(ImmutableList.toImmutableList());
    
    Bom bom = new Bom(coordinates, artifacts);
    return bom;
  }

  /**
   * Parses the dependencyManagement section of an artifact and returns
   * the artifacts included there.
   *
   * @param mavenRepositoryUrls URLs of Maven repositories to search for BOM members
   */
  public static Bom readBom(String coordinates, List<String> mavenRepositoryUrls)
      throws ArtifactDescriptorException {
    Artifact artifact = new DefaultArtifact(coordinates);
  
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);
  
    ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
  
    for (String repositoryUrl : mavenRepositoryUrls) {
      request.addRepository(RepositoryUtility.mavenRepositoryFromUrl(repositoryUrl));
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
      if (!shouldSkipBomMember(managed) && !managedDependencies.contains(managed)) {
        managedDependencies.add(managed);
      }
    }
    
    Bom bom = new Bom(coordinates, ImmutableList.copyOf(managedDependencies));
    return bom;
  }

  /**
   * Parses the dependencyManagement section of an artifact and returns the artifacts
   * included there.
   */
  public static Bom readBom(String coordinates) throws ArtifactDescriptorException {
    return Bom.readBom(coordinates, ImmutableList.of(RepositoryUtility.CENTRAL.getUrl()));
  }

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

    // Skipping grpc-android as it is not used by Google Cloud Client Libraries for Java. Checking
    // for availability of
    // this unused artifact on Maven Central has caused BOM validation check to fail in the past. See
    // https://github.com/googleapis/sdk-platform-java/pull/1989#issuecomment-1724039670
    if ("grpc-android".equals(artifact.getArtifactId())) {
      return true;
    }

    return false;
  }
}
