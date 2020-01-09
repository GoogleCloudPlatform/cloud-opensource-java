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
package com.google.cloud.tools.opensource.classpath;

import static org.junit.Assert.assertEquals;

import com.google.cloud.tools.opensource.dependencies.ArtifactProblem;
import com.google.common.collect.ImmutableList;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

public class ArtifactProblemTest {
  private Artifact artifactA = new DefaultArtifact("foo:a:1.0.0");
  private DependencyNode nodeA = new DefaultDependencyNode(artifactA);
  private Artifact artifactB = new DefaultArtifact("foo:b:1.0.0");
  private DependencyNode nodeB = new DefaultDependencyNode(artifactA);
  private Artifact artifactC = new DefaultArtifact("foo:b:1.0.0");
  private DependencyNode nodeC = new DefaultDependencyNode(artifactA);

  @Test
  public void testToString_oneInvalidClassFileInArtifact() {
    ArtifactProblem problemOneClass =
        ArtifactProblem.invalidClassFileInArtifact(
            ImmutableList.of(nodeA, nodeB, nodeC), ImmutableList.of("foo.bar.Class"));

    assertEquals(
        "foo:a:jar:1.0.0 contains an invalid class file foo.bar.Class. "
            + "Dependency path: [foo:a:jar:1.0.0, foo:a:jar:1.0.0, foo:a:jar:1.0.0]",
        problemOneClass.toString());
  }

  @Test
  public void testToString_invalidClassFilesInArtifact() {
    ArtifactProblem problemTwoClasses =
        ArtifactProblem.invalidClassFileInArtifact(
            ImmutableList.of(nodeA, nodeB, nodeC),
            ImmutableList.of("foo.bar.Class1", "foo.bar.Class2"));

    assertEquals(
        "foo:a:jar:1.0.0 contains 2 invalid class files (example: foo.bar.Class1). "
            + "Dependency path: [foo:a:jar:1.0.0, foo:a:jar:1.0.0, foo:a:jar:1.0.0]",
        problemTwoClasses.toString());
  }

  @Test
  public void testToString_unresolvableArtifactWithoutDependencyPath() {
    ArtifactProblem problem = ArtifactProblem.unresolvableArtifactUnknownDependencyPath(artifactA);

    assertEquals(
        "foo:a:jar:1.0.0 was not resolved. Dependency path is unknown.", problem.toString());
  }

  @Test
  public void testToString_unresolvableArtifact() {
    ArtifactProblem problem =
        ArtifactProblem.unresolvableArtifact(ImmutableList.of(nodeA, nodeB, nodeC));

    assertEquals(
        "foo:a:jar:1.0.0 was not resolved. Dependency path: [foo:a:jar:1.0.0, "
            + "foo:a:jar:1.0.0, foo:a:jar:1.0.0]",
        problem.toString());
  }
}
