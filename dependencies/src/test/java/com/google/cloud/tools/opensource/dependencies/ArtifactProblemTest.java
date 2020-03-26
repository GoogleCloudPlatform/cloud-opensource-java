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

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

public class ArtifactProblemTest {
  private Artifact artifactA = new DefaultArtifact("foo:a:1.0.0");
  private DependencyNode nodeA = new DefaultDependencyNode(new Dependency(artifactA, "compile"));
  private Artifact artifactB = new DefaultArtifact("foo:b:1.0.0");
  private DependencyNode nodeB = new DefaultDependencyNode(new Dependency(artifactB, "provided"));
  private Artifact artifactC = new DefaultArtifact("foo:c:1.0.0");
  private DependencyNode nodeC = new DefaultDependencyNode(new Dependency(artifactC, "runtime"));

  @Test
  public void testFormatProblems_2problems() {
    UnresolvableArtifactProblem problemA_B_C =
        new UnresolvableArtifactProblem(ImmutableList.of(nodeA, nodeB, nodeC));
    UnresolvableArtifactProblem problemB_C =
        new UnresolvableArtifactProblem(ImmutableList.of(nodeA, nodeB, nodeC));

    String actual = ArtifactProblem.formatProblems(ImmutableList.of(problemB_C, problemA_B_C));

    assertEquals(
        "foo:c:jar:1.0.0 was not resolved. "
            + "Dependency path: foo:a:jar:1.0.0 (compile) > foo:b:jar:1.0.0 (provided) > "
            + "foo:c:jar:1.0.0 (runtime) and a problem on the same artifact.\n",
        actual);
  }

  @Test
  public void testFormatProblems_3problems() {
    UnresolvableArtifactProblem problemA_B_C =
        new UnresolvableArtifactProblem(ImmutableList.of(nodeA, nodeB, nodeC));
    UnresolvableArtifactProblem problemB_C =
        new UnresolvableArtifactProblem(ImmutableList.of(nodeA, nodeB, nodeC));
    UnresolvableArtifactProblem problemC =
        new UnresolvableArtifactProblem(ImmutableList.of(nodeA, nodeB, nodeC));

    String actual =
        ArtifactProblem.formatProblems(ImmutableList.of(problemC, problemB_C, problemA_B_C));

    assertEquals(
        "foo:c:jar:1.0.0 was not resolved. Dependency path: "
            + "foo:a:jar:1.0.0 (compile) > foo:b:jar:1.0.0 (provided) > foo:c:jar:1.0.0 (runtime) "
            + "and 2 problems on the same artifact.\n",
        actual);
  }

  @Test
  public void testFormatProblems_2artifacts() {
    UnresolvableArtifactProblem problemA = new UnresolvableArtifactProblem(ImmutableList.of(nodeA));
    UnresolvableArtifactProblem problemB = new UnresolvableArtifactProblem(ImmutableList.of(nodeB));

    String actual = ArtifactProblem.formatProblems(ImmutableList.of(problemA, problemB));

    assertEquals(
        "foo:a:jar:1.0.0 was not resolved. Dependency path: foo:a:jar:1.0.0 (compile)\n"
            + "foo:b:jar:1.0.0 was not resolved. Dependency path: foo:b:jar:1.0.0 (provided)\n",
        actual);
  }

  @Test
  public void testEquality() {
    UnresolvableArtifactProblem problemA = new UnresolvableArtifactProblem(ImmutableList.of(nodeA));

    Artifact artifactACopy = new DefaultArtifact("foo:a:1.0.0");
    DependencyNode nodeACopy = new DefaultDependencyNode(new Dependency(artifactACopy, "compile"));
    UnresolvableArtifactProblem problemACopy =
        new UnresolvableArtifactProblem(ImmutableList.of(nodeACopy));

    DependencyNode nodeAWithProvidedScope =
        new DefaultDependencyNode(new Dependency(artifactACopy, "provided"));
    UnresolvableArtifactProblem problemAWithProvidedScope =
        new UnresolvableArtifactProblem(ImmutableList.of(nodeAWithProvidedScope));

    UnresolvableArtifactProblem problemB = new UnresolvableArtifactProblem(ImmutableList.of(nodeB));

    new EqualsTester()
        .addEqualityGroup(problemA, problemACopy)
        .addEqualityGroup(problemB)
        .addEqualityGroup(problemAWithProvidedScope)
        .testEquals();
  }
}
