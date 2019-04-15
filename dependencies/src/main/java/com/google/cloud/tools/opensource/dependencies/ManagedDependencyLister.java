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

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

/**
 * Demo retrieving list of managed dependencies from Maven coordinates.
 */
class ManagedDependencyLister {

  private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();

  public static void main(String[] args) throws ArtifactDescriptorException {
    DefaultArtifact artifact =
        new DefaultArtifact("com.google.cloud:bom:pom:1.0.0-SNAPSHOT");

    RepositorySystemSession session = RepositoryUtility.newSession(system);

    ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
    request.addRepository(RepositoryUtility.CENTRAL);
    request.setArtifact(artifact);

    ArtifactDescriptorResult resolved = system.readArtifactDescriptor(session, request);
    for (Dependency dependency : resolved.getManagedDependencies()) {
      System.out.println(dependency);
    }
  }

}
