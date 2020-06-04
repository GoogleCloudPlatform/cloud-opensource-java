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

package com.google.cloud.tools.opensource.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class DependencyGraphBuilderIntegrationTest {

  private Correspondence<UnresolvableArtifactProblem, String> problemOnArtifact =
      Correspondence.transforming(
          (UnresolvableArtifactProblem problem) -> Artifacts.toCoordinates(problem.getArtifact()),
          "has artifact");

  @Test
  public void testConfigureAdditionalMavenRepositories_notToUseMavenCentral()
      throws IOException {

    DependencyGraphBuilder graphBuilder = 
        new DependencyGraphBuilder(ImmutableList.of("https://dl.google.com/dl/android/maven2"));

    File localRepository = Files.createTempDirectory(".m2").toFile();
    localRepository.deleteOnExit();

    graphBuilder.setLocalRepository(localRepository.toPath());
    
    // This artifact does not exist in Android's repository
    Artifact artifact = new DefaultArtifact("com.google.guava:guava:15.0-rc1");

    DependencyGraph result = graphBuilder.buildFullDependencyGraph(ImmutableList.of(artifact));
    Truth.assertThat(result.getUnresolvedArtifacts())
        .comparingElementsUsing(problemOnArtifact)
        .contains("com.google.guava:guava:15.0-rc1");
  }

}
