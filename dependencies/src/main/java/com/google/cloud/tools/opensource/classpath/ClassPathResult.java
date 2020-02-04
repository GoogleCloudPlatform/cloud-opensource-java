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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.UnresolvableArtifactProblem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import java.nio.file.Path;

/** Result of class path resolution with {@link UnresolvableArtifactProblem}s if any. */
public final class ClassPathResult {

  private final ImmutableList<Path> classPath;

  /**
   * An ordered map of absolute paths of JAR files to one or more Maven dependency paths.
   *
   * <p>The keys of the returned map represent jar files of {@code artifacts} and their transitive
   * dependencies. The return value of {@link LinkedListMultimap#keySet()} preserves key iteration
   * order.
   *
   * <p>The values of the returned map for a key (jar file) represent the different Maven dependency
   * paths from {@code artifacts} to the Maven artifact of the jar file.
   */
  private final ImmutableListMultimap<Path, DependencyPath> dependencyPaths;

  private final ImmutableList<UnresolvableArtifactProblem> artifactProblems;

  public ClassPathResult(
      Multimap<Path, DependencyPath> dependencyPaths,
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
   * Returns the dependency path to the JAR file. An empty list if the JAR file is not in the class
   * path.
   */
  public ImmutableList<DependencyPath> getDependencyPaths(Path jar) {
    return dependencyPaths.get(jar);
  }

  /** Returns problems encountered while constructing the dependency graph. */
  ImmutableList<UnresolvableArtifactProblem> getArtifactProblems() {
    return artifactProblems;
  }

  /** Returns text describing dependency paths to {@code jars} in the dependency tree. */
  public String formatDependencyPaths(Iterable<Path> jars) {
    StringBuilder message = new StringBuilder();
    for (Path jar : jars) {
      ImmutableList<DependencyPath> dependencyPaths = getDependencyPaths(jar);
      checkArgument(dependencyPaths.size() >= 1, "%s is not in the class path", jar);

      message.append(jar.getFileName() + " is at:\n");

      int otherCount = dependencyPaths.size() - 1;
      message.append("  " + dependencyPaths.get(0) + "\n");
      if (otherCount == 1) {
        message.append("  and 1 dependency path.\n");
      } else if (otherCount > 1) {
        message.append("  and " + otherCount + " other dependency paths.\n");
      }
    }
    return message.toString();
  }
}
