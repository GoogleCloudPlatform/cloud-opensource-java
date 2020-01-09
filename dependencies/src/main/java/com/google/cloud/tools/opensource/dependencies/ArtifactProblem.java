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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

/** Problem in a Maven artifact. */
public final class ArtifactProblem {

  /** The Maven artifact that has the problem. */
  private final Artifact artifact;

  /** The type of the problem in the artifact. */
  private final Type type;

  /**
   * The dependency path to the artifact from the root of the dependency tree. An empty list if the
   * path is unknown.
   */
  private final ImmutableList<DependencyNode> dependencyPath;

  /**
   * The list of invalid class files if type is {@link Type#INVALID_CLASS_FILE}; otherwise an empty
   * list.
   */
  private final ImmutableList<String> classFileNames;

  /**
   * Returns the problem describing the artifact at the leaf of {@code dependencyPath} is not
   * resolvable by Maven.
   */
  public static ArtifactProblem unresolvableArtifact(List<DependencyNode> dependencyPath) {
    checkNotNull(dependencyPath);
    checkArgument(dependencyPath.size() > 0, "dependencyPath should not be empty");

    DependencyNode lastElement = dependencyPath.get(dependencyPath.size() - 1);
    return new ArtifactProblem(
        Type.UNRESOLVABLE_ARTIFACT, lastElement.getArtifact(), dependencyPath, ImmutableList.of());
  }

  /**
   * Returns the problem describing the artifact is not resolvable by Maven. This method is used
   * when the dependency path to the artifact in the dependency tree is unknown.
   */
  public static ArtifactProblem unresolvableArtifactUnknownDependencyPath(Artifact artifact) {
    return new ArtifactProblem(
        Type.UNRESOLVABLE_ARTIFACT, artifact, ImmutableList.of(), ImmutableList.of());
  }

  /** Returns the problems describing the artifact contains invalid class files. */
  public static ArtifactProblem invalidClassFileInArtifact(
      List<DependencyNode> dependencyPath, List<String> classFileNames) {
    checkNotNull(dependencyPath);
    checkArgument(!dependencyPath.isEmpty(), "dependencyPath should not be empty");

    DependencyNode lastElement = dependencyPath.get(dependencyPath.size() - 1);
    return new ArtifactProblem(
        Type.INVALID_CLASS_FILE, lastElement.getArtifact(), dependencyPath, classFileNames);
  }

  private ArtifactProblem(
      Type type,
      Artifact artifact,
      @Nullable List<DependencyNode> dependencyPath,
      List<String> classFileNames) {
    this.type = checkNotNull(type);
    this.artifact = checkNotNull(artifact);
    this.dependencyPath = ImmutableList.copyOf(dependencyPath);
    this.classFileNames = ImmutableList.copyOf(classFileNames);
  }

  private enum Type {
    /** When the JAR file of the artifact contains one or more invalid class files. */
    INVALID_CLASS_FILE,
    /** When the artifact is unresolvable. */
    UNRESOLVABLE_ARTIFACT,
  }

  @Override
  public String toString() {
    switch (type) {
      case UNRESOLVABLE_ARTIFACT:
        if (dependencyPath.isEmpty()) {
          return artifact + " was not resolved. Dependency path is unknown.";
        } else {
          return artifact + " was not resolved. Dependency path: " + dependencyPath;
        }
      case INVALID_CLASS_FILE:
        int classFileCount = classFileNames.size();

        String classFileDescription;
        if (classFileCount == 1) {
          classFileDescription = "an invalid class file " + classFileNames.get(0) + ".";
        } else {
          classFileDescription =
              classFileCount + " invalid class files (example: " + classFileNames.get(0) + ").";
        }
        return artifact
            + " contains "
            + classFileDescription
            + " Dependency path: "
            + dependencyPath;
    }
    throw new IllegalStateException("Unexpected type " + type + " to describe artifact problem");
  }
}
