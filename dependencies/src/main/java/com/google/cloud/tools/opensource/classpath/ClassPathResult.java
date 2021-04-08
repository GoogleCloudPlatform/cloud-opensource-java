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
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import org.eclipse.aether.artifact.Artifact;

/** Result of class path resolution with {@link UnresolvableArtifactProblem}s if any. */
public final class ClassPathResult {

  private final ImmutableList<ClassPathEntry> classPath;

  private final AnnotatedClassPath annotatedClassPath;

  private final ImmutableList<UnresolvableArtifactProblem> artifactProblems;

  public ClassPathResult(
      AnnotatedClassPath dependencyPaths, Iterable<UnresolvableArtifactProblem> artifactProblems) {
    this.annotatedClassPath = dependencyPaths;
    this.classPath = dependencyPaths.getClassPath();
    this.artifactProblems = ImmutableList.copyOf(artifactProblems);
  }

  /** Returns the resolved class path. */
  public ImmutableList<ClassPathEntry> getClassPath() {
    return classPath;
  }

  /**
   * Returns all paths to the class path entry or 
   * an empty list if the entry is not in the class path.
   */
  public ImmutableList<DependencyPath> getDependencyPaths(ClassPathEntry entry) {
    return annotatedClassPath.pathsTo(entry);
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
   * Returns the classpath entries for the transitive dependencies of the specified
   * artifact.
   */
  public ImmutableSet<ClassPathEntry> getClassPathEntries(String coordinates) {
    ImmutableSet.Builder<ClassPathEntry> builder = ImmutableSet.builder();
    for (ClassPathEntry entry : classPath) {
      for (DependencyPath dependencyPath : annotatedClassPath.pathsTo(entry)) {
        if (dependencyPath.size() > 1) {
          Artifact artifact = dependencyPath.get(1);
          if (Artifacts.toCoordinates(artifact).equals(coordinates)) {
            builder.add(entry);
          }
        }
      }
    }
    return builder.build();
  }

  /**
   * Returns the class path entry for the artifact that matches {@code groupId} and {@code
   * artifactId}. {@code Null} if no matching artifact is found.
   */
  ClassPathEntry findEntryById(String groupId, String artifactId) {
    for (ClassPathEntry entry : getClassPath()) {
      Artifact artifact = entry.getArtifact();
      if (artifact.getGroupId().equals(groupId) && artifact.getArtifactId().equals(artifactId)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * Returns the class path entry that contains the class of {@code symbol}. {@code Null} if no
   * matching entry is found.
   */
  ClassPathEntry findEntryBySymbol(Symbol symbol) throws IOException {
    String className = symbol.getClassBinaryName();
    for (ClassPathEntry entry : getClassPath()) {
      if (entry.getFileNames().contains(className)) {
        return entry;
      }
    }
    return null;
  }
}
