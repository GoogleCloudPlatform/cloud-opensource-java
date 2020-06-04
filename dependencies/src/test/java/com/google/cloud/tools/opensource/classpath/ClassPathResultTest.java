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
import com.google.common.collect.UnmodifiableIterator;
import java.nio.file.Paths;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class ClassPathResultTest {
  private Artifact artifactA =
      new DefaultArtifact("com.google:a:1").setFile(Paths.get("a.jar").toFile());
  private Artifact artifactB =
      new DefaultArtifact("com.google:b:1").setFile(Paths.get("b.jar").toFile());
  private DependencyPath dependencyPath_A =
      new DependencyPath(null).append(new Dependency(artifactA, "compile"));
  private DependencyPath dependencyPath_B =
      new DependencyPath(null).append(new Dependency(artifactB, "compile"));
  private DependencyPath dependencyPath_B_A =
      new DependencyPath(null)
          .append(new Dependency(artifactB, "compile"))
          .append(new Dependency(artifactA, "compile"));
  private DependencyPath dependencyPath_A_B_A =
      new DependencyPath(null)
          .append(new Dependency(artifactA, "compile"))
          .append(new Dependency(artifactB, "compile"))
          .append(new Dependency(artifactA, "compile"));
  private ClassPathEntry jarA = new ClassPathEntry(artifactA);
  private ClassPathEntry jarB = new ClassPathEntry(artifactB);
  ;

  @Test
  public void testFormatDependencyPaths_onePath() {
    ImmutableListMultimap<ClassPathEntry, DependencyPath> tree =
        ImmutableListMultimap.of(jarA, dependencyPath_A, jarB, dependencyPath_B);

    ClassPath classPathResult = new ClassPath(tree, ImmutableSet.of());

    String actual = classPathResult.formatDependencyPaths(ImmutableList.of(jarA));

    assertEquals("com.google:a:1 is at:\n" + "  com.google:a:1 (compile)\n", actual);
  }

  @Test
  public void testFormatDependencyPaths_path_A_B() {
    ImmutableListMultimap<ClassPathEntry, DependencyPath> tree =
        ImmutableListMultimap.of(jarA, dependencyPath_A, jarB, dependencyPath_B);

    ClassPath classPathResult = new ClassPath(tree, ImmutableSet.of());

    String actual = classPathResult.formatDependencyPaths(ImmutableList.of(jarA, jarB));

    assertEquals(
        "com.google:a:1 is at:\n"
            + "  com.google:a:1 (compile)\n"
            + "com.google:b:1 is at:\n"
            + "  com.google:b:1 (compile)\n",
        actual);
  }

  @Test
  public void testFormatDependencyPaths_twoPathsForA() {
    ImmutableListMultimap<ClassPathEntry, DependencyPath> tree =
        ImmutableListMultimap.of(jarA, dependencyPath_A, jarA, dependencyPath_B_A);

    ClassPath classPathResult = new ClassPath(tree, ImmutableSet.of());

    String actual = classPathResult.formatDependencyPaths(ImmutableList.of(jarA));

    assertEquals(
        "com.google:a:1 is at:\n" + "  com.google:a:1 (compile)\n" + "  and 1 dependency path.\n",
        actual);
  }

  @Test
  public void testFormatDependencyPaths_threePathsForA() {
    ImmutableListMultimap<ClassPathEntry, DependencyPath> tree =
        ImmutableListMultimap.of(
            jarA, dependencyPath_A, jarA, dependencyPath_B_A, jarA, dependencyPath_A_B_A);

    ClassPath classPathResult = new ClassPath(tree, ImmutableSet.of());

    String actual = classPathResult.formatDependencyPaths(ImmutableList.of(jarA));

    assertEquals(
        "com.google:a:1 is at:\n"
            + "  com.google:a:1 (compile)\n"
            + "  and 2 other dependency paths.\n",
        actual);
  }

  @Test
  public void testFormatDependencyPaths_irrelevantJar() {
    ImmutableListMultimap<ClassPathEntry, DependencyPath> tree =
        ImmutableListMultimap.of(jarA, dependencyPath_A);

    ClassPath classPathResult = new ClassPath(tree, ImmutableSet.of());

    try {
      classPathResult.formatDependencyPaths(ImmutableList.of(jarB));
      fail("The irrelevant JAR file should be invalidated.");
    } catch (IllegalArgumentException expected) {
      assertEquals("com.google:b:1 is not in the class path", expected.getMessage());
    }
  }

  @Test
  public void testGetClassPathEntries() {
    ImmutableListMultimap<ClassPathEntry, DependencyPath> tree =
        ImmutableListMultimap.of(
            jarA, dependencyPath_A,
            jarB, dependencyPath_B,
            jarA, dependencyPath_A_B_A);

    ClassPath result = new ClassPath(tree, ImmutableSet.of());

    ImmutableSet<ClassPathEntry> classPathEntries = result.getClassPathEntries("com.google:a:1");
    assertEquals(1, classPathEntries.size());
    UnmodifiableIterator<ClassPathEntry> iterator = classPathEntries.iterator();
    assertEquals(Paths.get("a.jar"), iterator.next().getJar());
  }
}
