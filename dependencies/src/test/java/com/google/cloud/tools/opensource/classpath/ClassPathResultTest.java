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
import static org.junit.Assert.fail;

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Before;
import org.junit.Test;

public class ClassPathResultTest {
  private Artifact artifactA = new DefaultArtifact("com.google:a:1");
  private Artifact artifactB = new DefaultArtifact("com.google:b:1");
  private DependencyPath dependencyPath_A = new DependencyPath();
  private DependencyPath dependencyPath_B = new DependencyPath();
  private DependencyPath dependencyPath_B_A = new DependencyPath();
  private DependencyPath dependencyPath_A_B_A = new DependencyPath();
  private Path jarA = Paths.get("a.jar");
  private Path jarB = Paths.get("b.jar");

  @Before
  public void setup() {
    dependencyPath_A.add(new Dependency(artifactA, "compile"));

    dependencyPath_B.add(new Dependency(artifactB, "compile"));

    dependencyPath_B_A.add(new Dependency(artifactB, "compile"));
    dependencyPath_B_A.add(new Dependency(artifactA, "compile"));

    dependencyPath_A_B_A.add(new Dependency(artifactA, "compile"));
    dependencyPath_A_B_A.add(new Dependency(artifactB, "compile"));
    dependencyPath_A_B_A.add(new Dependency(artifactA, "compile"));
  }

  @Test
  public void testFormatDependencyPaths_onePath() {
    ImmutableListMultimap<Path, DependencyPath> tree =
        ImmutableListMultimap.of(jarA, dependencyPath_A, jarB, dependencyPath_B);

    ClassPathResult classPathResult = new ClassPathResult(tree, ImmutableSet.of());

    String actual = classPathResult.formatDependencyPaths(ImmutableList.of(jarA));

    assertEquals("a.jar is at:\n" + "  com.google:a:1 (compile)\n", actual);
  }

  @Test
  public void testFormatDependencyPaths_path_A_B() {
    ImmutableListMultimap<Path, DependencyPath> tree =
        ImmutableListMultimap.of(jarA, dependencyPath_A, jarB, dependencyPath_B);

    ClassPathResult classPathResult = new ClassPathResult(tree, ImmutableSet.of());

    String actual = classPathResult.formatDependencyPaths(ImmutableList.of(jarA, jarB));

    assertEquals(
        "a.jar is at:\n"
            + "  com.google:a:1 (compile)\n"
            + "b.jar is at:\n"
            + "  com.google:b:1 (compile)\n",
        actual);
  }

  @Test
  public void testFormatDependencyPaths_twoPathsForA() {
    ImmutableListMultimap<Path, DependencyPath> tree =
        ImmutableListMultimap.of(jarA, dependencyPath_A, jarA, dependencyPath_B_A);

    ClassPathResult classPathResult = new ClassPathResult(tree, ImmutableSet.of());

    String actual = classPathResult.formatDependencyPaths(ImmutableList.of(jarA));

    assertEquals(
        "a.jar is at:\n" + "  com.google:a:1 (compile)\n" + "  and 1 dependency path.\n", actual);
  }

  @Test
  public void testFormatDependencyPaths_threePathsForA() {
    ImmutableListMultimap<Path, DependencyPath> tree =
        ImmutableListMultimap.of(
            jarA, dependencyPath_A, jarA, dependencyPath_B_A, jarA, dependencyPath_A_B_A);

    ClassPathResult classPathResult = new ClassPathResult(tree, ImmutableSet.of());

    String actual = classPathResult.formatDependencyPaths(ImmutableList.of(jarA));

    assertEquals(
        "a.jar is at:\n" + "  com.google:a:1 (compile)\n" + "  and 2 other dependency paths.\n",
        actual);
  }

  @Test
  public void testFormatDependencyPaths_irrelevantJar() {
    ImmutableListMultimap<Path, DependencyPath> tree =
        ImmutableListMultimap.of(jarA, dependencyPath_A);

    ClassPathResult classPathResult = new ClassPathResult(tree, ImmutableSet.of());

    try {
      classPathResult.formatDependencyPaths(ImmutableList.of(jarB));
      fail("The irrelevant JAR file should be invalidated.");
    } catch (IllegalArgumentException ex) {
      // pass
      assertEquals("b.jar is not in the class path", ex.getMessage());
    }
  }
}
