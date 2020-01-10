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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

public class InvalidClassFileProblemTest {
  private Artifact artifactA = new DefaultArtifact("foo:a:1.0.0");
  private DependencyNode nodeA = new DefaultDependencyNode(new Dependency(artifactA, "compile"));
  private Artifact artifactB = new DefaultArtifact("foo:b:1.0.0");
  private DependencyNode nodeB = new DefaultDependencyNode(new Dependency(artifactB, "provided"));
  private Artifact artifactC = new DefaultArtifact("foo:c:1.0.0");
  private DependencyNode nodeC = new DefaultDependencyNode(new Dependency(artifactC, "runtime"));

  @Test
  public void testToString_oneInvalidClassFileInArtifact() {
    InvalidClassFileProblem problemOneClass =
        new InvalidClassFileProblem(
            ImmutableList.of(nodeA, nodeB, nodeC), ImmutableList.of("foo.bar.Class"));

    assertEquals(
        "foo:c:jar:1.0.0 contains an invalid class file foo.bar.Class. "
            + "Dependency path: "
            + "foo:a:jar:1.0.0 (compile) > foo:b:jar:1.0.0 (provided) > foo:c:jar:1.0.0 (runtime)",
        problemOneClass.toString());
  }

  @Test
  public void testToString_invalidClassFilesInArtifact() {
    InvalidClassFileProblem problemTwoClasses =
        new InvalidClassFileProblem(
            ImmutableList.of(nodeA, nodeB, nodeC),
            ImmutableList.of("foo.bar.Class1", "foo.bar.Class2"));

    assertEquals(
        "foo:c:jar:1.0.0 contains 2 invalid class files (example: foo.bar.Class1). "
            + "Dependency path: "
            + "foo:a:jar:1.0.0 (compile) > foo:b:jar:1.0.0 (provided) > foo:c:jar:1.0.0 (runtime)",
        problemTwoClasses.toString());
  }

  @Test
  public void testInvalidInput_emptyClassFileNames() {
    try {
      new InvalidClassFileProblem(ImmutableList.of(nodeA, nodeB, nodeC), ImmutableList.of());
      fail("The constructor should invalidate empty classFileNames");
    } catch (IllegalArgumentException ex) {
      // pass
      assertEquals("ClassFileNames cannot be empty", ex.getMessage());
    }
  }
}
