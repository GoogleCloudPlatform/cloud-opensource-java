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

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.UnresolvableArtifactProblem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.LinkedListMultimap;
import java.nio.file.Path;

/** Result of class path resolution with {@link UnresolvableArtifactProblem}s if any. */
public final class ClassPathResult {

  private final ImmutableList<Path> classPath;
  private final ImmutableListMultimap<Path, DependencyPath> dependencyPaths;
  private final ImmutableList<UnresolvableArtifactProblem> artifactProblems;

  ClassPathResult(
      LinkedListMultimap<Path, DependencyPath> dependencyPaths,
      Iterable<UnresolvableArtifactProblem> artifactProblems) {
    this.dependencyPaths = ImmutableListMultimap.copyOf(dependencyPaths);
    this.classPath = ImmutableList.copyOf(dependencyPaths.keySet());
    this.artifactProblems = ImmutableList.copyOf(artifactProblems);
  }
  ;

  /** Returns the list of absolute paths to JAR files of resolved Maven artifacts. */
  public ImmutableList<Path> getClassPath() {
    return classPath;
  }

  /**
   * Returns an ordered map of absolute paths of JAR files to one or more Maven dependency paths.
   *
   * <p>The keys of the returned map represent jar files of {@code artifacts} and their transitive
   * dependencies. The return value of {@link LinkedListMultimap#keySet()} preserves key iteration
   * order.
   *
   * <p>The values of the returned map for a key (jar file) represent the different Maven dependency
   * paths from {@code artifacts} to the Maven artifact of the jar file.
   */
  public ImmutableListMultimap<Path, DependencyPath> getDependencyPaths() {
    return dependencyPaths;
  }

  /** Returns problems encountered while constructing the dependency graph. */
  ImmutableList<UnresolvableArtifactProblem> getArtifactProblems() {
    return artifactProblems;
  }
}
