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

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.aether.graph.DependencyNode;

/** Problem describing a Maven artifact containing invalid class files. */
public final class InvalidClassFileProblem extends ArtifactProblem {
  /** The invalid class file names. Never empty. */
  private final ImmutableList<String> classFileNames;

  InvalidClassFileProblem(List<DependencyNode> dependencyPath, List<String> classFileNames) {
    super(dependencyPath.get(dependencyPath.size() - 1).getArtifact(), dependencyPath);
    checkArgument(!classFileNames.isEmpty(), "ClassFileNames should not be empty");
    this.classFileNames = ImmutableList.copyOf(classFileNames);
  }

  @Override
  public String toString() {
    int classFileCount = classFileNames.size();

    String classFileDescription;
    if (classFileCount == 1) {
      classFileDescription = "an invalid class file " + classFileNames.get(0) + ".";
    } else {
      classFileDescription =
          classFileCount + " invalid class files (example: " + classFileNames.get(0) + ").";
    }
    return artifact + " contains " + classFileDescription + " Dependency path: " + getPath();
  }
}
