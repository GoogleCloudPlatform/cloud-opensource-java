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

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.UnresolvableArtifactProblem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import org.eclipse.aether.artifact.Artifact;

/** Result of class path resolution with {@link UnresolvableArtifactProblem}s if any. */
public final class ClassPathResult {

  private final ImmutableList<ClassPathEntry> classPath;

  /**
   * An ordered map from class path elements to one or more Maven dependency paths.
   *
   * <p>The keys of the returned map represent Maven artifacts in the resolved class path, including
   * ones in the transitive dependency. The return value of {@link LinkedListMultimap#keySet()}
   * preserves key iteration order.
   *
   * <p>The values of the returned map for a key (class path entry) represent the different Maven
   * dependency paths from {@code artifacts} to the Maven artifact.
   */
  private final ImmutableListMultimap<ClassPathEntry, DependencyPath> dependencyPaths;

  private final ImmutableList<UnresolvableArtifactProblem> artifactProblems;

  public ClassPathResult(
      Multimap<ClassPathEntry, DependencyPath> dependencyPaths,
      Iterable<UnresolvableArtifactProblem> artifactProblems) {
    this.dependencyPaths = ImmutableListMultimap.copyOf(dependencyPaths);
    this.classPath = ImmutableList.copyOf(dependencyPaths.keySet());
    this.artifactProblems = ImmutableList.copyOf(artifactProblems);
  }

  /** Returns the resolved class path. */
  public ImmutableList<ClassPathEntry> getClassPath() {
    return classPath;
  }

  /**
   * Returns the dependency path to the class path entry. An empty list if the entry is not in
   * the class path.
   */
  public ImmutableList<DependencyPath> getDependencyPaths(ClassPathEntry entry) {
    return dependencyPaths.get(entry);
  }

  /** Returns problems encountered while constructing the dependency graph. */
  public ImmutableList<UnresolvableArtifactProblem> getArtifactProblems() {
    return artifactProblems;
  }

  /** Returns text describing dependency paths to class path entries in the dependency tree. */
  public String formatDependencyPaths(Iterable<ClassPathEntry> entries) {
    StringBuilder message = new StringBuilder();
    for (ClassPathEntry entry : entries) {
      ImmutableList<DependencyPath> dependencyPaths = getDependencyPaths(entry);
      checkArgument(dependencyPaths.size() >= 1, "%s is not in the class path", entry);

      message.append(entry + " is at:\n");

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

  /**
   * Returns mapping from the Maven coordinates to {@link ClassPathEntry}. The keys are the
   * coordinates of the direct dependencies of the root nodes in {@link #dependencyPaths}. The
   * values are all {@link ClassPathEntry}s in the subtree of the key.
   */
  public ImmutableSetMultimap<String, ClassPathEntry> coordinatesToClassPathEntry() {
    Builder<String, ClassPathEntry> coordinatesToEntry = ImmutableSetMultimap.builder();
    for (ClassPathEntry entry : getClassPath()) {
      for (DependencyPath dependencyPath : getDependencyPaths(entry)) {
        Artifact artifact = dependencyPath.get(1);
        coordinatesToEntry.put(Artifacts.toCoordinates(artifact), entry);
      }
    }

    return coordinatesToEntry.build();
  }
}
