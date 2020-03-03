/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.dependencies.linkagemonitor;

import static com.google.cloud.tools.opensource.dependencies.RepositoryUtility.CENTRAL;

import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.Test;

public class VersionSubstitutingModelResolverTest {
  private static final DefaultModelBuilder modelBuilder =
      new DefaultModelBuilderFactory().newInstance();

  private final RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();
  private final RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);
  private final DefaultRemoteRepositoryManager remoteRepositoryManager =
      new DefaultRemoteRepositoryManager();

  private final Correspondence<Dependency, String> dependencyToCoordinates =
      Correspondence.transforming(
          (Dependency dependency) ->
              dependency.getGroupId()
                  + ":"
                  + dependency.getArtifactId()
                  + ":"
                  + dependency.getVersion(),
          "has dependency of coordinates");

  @Test
  public void testSubstitution() throws ArtifactResolutionException, ModelBuildingException {

    // Google-cloud-bom 0.121.0-alpha imports google-auth-library-bom 0.19.0.
    DefaultArtifact googleCloudBom =
        new DefaultArtifact("com.google.cloud:google-cloud-bom:pom:0.121.0-alpha");
    ArtifactResult bomResult =
        repositorySystem.resolveArtifact(
            session, new ArtifactRequest(googleCloudBom, ImmutableList.of(CENTRAL), null));

    ImmutableMap<String, String> substitution =
        ImmutableMap.of(
            "com.google.auth:google-auth-library-bom",
            "0.18.0" // This is intentionally different from 0.19.0
            );
    VersionSubstitutingModelResolver resolver =
        new VersionSubstitutingModelResolver(
            session,
            null,
            repositorySystem,
            remoteRepositoryManager,
            ImmutableList.of(CENTRAL), // Needed when parent pom is not locally available
            substitution);

    ModelBuildingRequest modelRequest = new DefaultModelBuildingRequest();
    modelRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    modelRequest.setPomFile(bomResult.getArtifact().getFile());
    modelRequest.setModelResolver(resolver);
    modelRequest.setSystemProperties(System.getProperties()); // for Java version property

    ModelBuildingResult result = modelBuilder.build(modelRequest);
    DependencyManagement dependencyManagement =
        result.getEffectiveModel().getDependencyManagement();

    Truth.assertWithMessage(
            "Google-cloud-bom's google-auth-library part should be substituted for a different"
                + " version.")
        .that(dependencyManagement.getDependencies())
        .comparingElementsUsing(dependencyToCoordinates)
        .contains("com.google.auth:google-auth-library-credentials:0.18.0");
  }

  @Test
  public void testNewCopy() throws UnresolvableModelException {
    ImmutableMap<String, String> substitution =
        ImmutableMap.of("com.google.guava:guava", "25.1-jre");
    VersionSubstitutingModelResolver resolver =
        new VersionSubstitutingModelResolver(
            session,
            null,
            repositorySystem,
            remoteRepositoryManager,
            ImmutableList.of(CENTRAL), // Needed when parent pom is not locally available
            substitution);

    ModelResolver copiedResolver = resolver.newCopy();
    Truth.assertThat(copiedResolver).isInstanceOf(VersionSubstitutingModelResolver.class);

    // request for guava:20.0 is replaced with 25.1-jre
    ModelSource guavaModelSource = copiedResolver.resolveModel("com.google.guava", "guava", "20.0");
    Truth.assertThat(guavaModelSource.getLocation()).contains("guava-25.1-jre.pom");
  }
}
