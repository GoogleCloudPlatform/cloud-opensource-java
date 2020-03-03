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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.project.ProjectModelResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Model resolver that substitutes the version of a POM specified in {@code versionSubstitution}
 * map. {@code VersionSubstitution}'s keys are versionless coordinates ({@code groupId:artifactId})
 * and the values are the version to use when resolving the corresponding artifacts.
 *
 * <p>For example, when {@code versionSubstitution} contains a key-value pair of {@code
 * "com.google.guava:guava-bom" → "25.1-android"}, {@link VersionSubstitutingModelResolver}
 * substitutes the version of model building requests that has {@code groupId:com.google.guava} and
 * {@code artifactId:guava-bom} with {@code 25.1-android}.
 */
class VersionSubstitutingModelResolver extends ProjectModelResolver {

  private final ImmutableMap<String, String> versionSubstitution;

  // Following fields are for newCopy method.
  private final RepositorySystemSession session;
  private final RequestTrace trace;
  private final RepositorySystem resolver;
  private final RemoteRepositoryManager remoteRepositoryManager;
  private final ImmutableList<RemoteRepository> repositories;

  VersionSubstitutingModelResolver(
      RepositorySystemSession session,
      RequestTrace trace,
      RepositorySystem resolver,
      RemoteRepositoryManager remoteRepositoryManager,
      List<RemoteRepository> repositories,
      Map<String, String> versionSubstitution) {
    super(session, trace, resolver, remoteRepositoryManager, repositories, null, null);
    this.session = session;
    this.trace = trace;
    this.resolver = resolver;
    this.remoteRepositoryManager = remoteRepositoryManager;
    this.repositories = ImmutableList.copyOf(repositories);
    this.versionSubstitution = ImmutableMap.copyOf(versionSubstitution);
  }

  /**
   * Returns a file model source for {@code groupId:artifactId:version}.
   * The version is substituted if {@link #versionSubstitution} contains the
   * versionless coordinates ({@code groupId:artifactId}) in its keys.
   */
  @Override
  public ModelSource2 resolveModel(String groupId, String artifactId, String version)
      throws UnresolvableModelException {
    String versionlessCoordinates = groupId + ":" + artifactId;
    String versionToResolve = versionSubstitution.getOrDefault(versionlessCoordinates, version);
    return (ModelSource2) super.resolveModel(groupId, artifactId, versionToResolve);
  }

  @Override
  public ModelResolver newCopy() {
    return new VersionSubstitutingModelResolver(
        session, trace, resolver, remoteRepositoryManager, repositories, versionSubstitution);
  }
}
