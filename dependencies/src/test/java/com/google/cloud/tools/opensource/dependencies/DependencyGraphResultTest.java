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
import static org.junit.Assert.assertSame;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

public class DependencyGraphResultTest {

  @Test
  public void testCreation() {
    DependencyGraph graph = new DependencyGraph();
    Artifact artifactA = new DefaultArtifact("foo:a:1.0.0");
    UnresolvableArtifactProblem problem = new UnresolvableArtifactProblem(artifactA);
    List<UnresolvableArtifactProblem> problems = ImmutableList.of(problem);

    DependencyGraphResult result = new DependencyGraphResult(graph, problems);

    assertSame(graph, result.getDependencyGraph());
    assertEquals(problem, result.getArtifactProblems().get(0));
  }
}
