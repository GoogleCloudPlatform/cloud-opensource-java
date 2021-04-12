/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.opensource.classpath;

import static org.junit.Assert.*;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;
import java.io.File;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.junit.Test;

public class GradleDependencyMediationTest {
  Artifact artifactA1 = new DefaultArtifact("g:a:1.0.0").setFile(new File("a-1.0.0.jar"));
  Artifact artifactA2 = new DefaultArtifact("g:a:2.0.0").setFile(new File("a-2.0.0.jar"));
  Artifact artifactB1 = new DefaultArtifact("g:b:1.0.0").setFile(new File("b-1.0.0.jar"));
  DependencyMediation mediation = DependencyMediation.GRADLE;

  Correspondence<ClassPathEntry, Artifact> CLASS_PATH_ENTRY_TO_ARTIFACT =
      Correspondence.transforming(ClassPathEntry::getArtifact, "has an artifact of");

  @Test
  public void testMediation_noDuplicates() throws InvalidVersionSpecificationException {

    DependencyGraph graph = new DependencyGraph(null);
    // The old version comes first in the graph.list
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactA1, "compile")));
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactA2, "compile")));
    // The duplicate shouldn't appear in the class path
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactA1, "compile")));
    AnnotatedClassPath result = mediation.mediate(graph);

    Truth.assertThat(result.getClassPath()).hasSize(1);

    // Gradle chooses the highest version
    Truth.assertThat(result.getClassPath())
        .comparingElementsUsing(CLASS_PATH_ENTRY_TO_ARTIFACT)
        .containsExactly(artifactA2);
  }

  @Test
  public void testMediation_oneArtifactForEachVersionlessCoordinates()
      throws InvalidVersionSpecificationException {

    DependencyGraph graph = new DependencyGraph(null);
    // The old version comes first in the graph.list
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactA1, "compile")));
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactA2, "compile")));
    // The duplicate shouldn't appear in the class path
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactB1, "compile")));
    AnnotatedClassPath result = mediation.mediate(graph);

    Truth.assertThat(result.getClassPath())
        .comparingElementsUsing(CLASS_PATH_ENTRY_TO_ARTIFACT)
        .containsExactly(artifactA2, artifactB1)
        .inOrder();
  }

  @Test
  public void testMediation_withEnforcedPlatform() throws InvalidVersionSpecificationException {

    DependencyGraph graph = new DependencyGraph(null);
    // The old version comes first in the graph.list
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactA1, "compile")));
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactA2, "compile")));
    // The duplicate shouldn't appear in the class path
    graph.addPath(new DependencyPath(null).append(new Dependency(artifactB1, "compile")));

    GradleDependencyMediation mediation =
        GradleDependencyMediation.withEnforcedPlatform(
            new Bom("g:bom:1.0.0", ImmutableList.of(artifactA1)));
    AnnotatedClassPath result = mediation.mediate(graph);

    Truth.assertThat(result.getClassPath())
        .comparingElementsUsing(CLASS_PATH_ENTRY_TO_ARTIFACT)
        .containsExactly(artifactA1, artifactB1)
        .inOrder();
  }
}
