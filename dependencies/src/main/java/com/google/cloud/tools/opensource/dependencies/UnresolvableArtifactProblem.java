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

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

/** Problem describing unresolvable Maven artifact in a dependency tree. */
public final class UnresolvableArtifactProblem extends ArtifactProblem {

  /**
   * Problem when Maven cannot resolve {@code artifact} in a dependency tree.
   *
   * <p>Prefer {@link #UnresolvableArtifactProblem(List)} when the dependency path to the artifact
   * is available, because it gives a more detailed error message.
   */
  public UnresolvableArtifactProblem(Artifact artifact) {
    super(artifact, ImmutableList.of());
  }

  /**
   * Problem when Maven cannot resolve the artifact at the leaf of {@code dependencyPath} in a
   * dependency tree.
   */
  public UnresolvableArtifactProblem(List<DependencyNode> dependencyPath) {
    super(dependencyPath.get(dependencyPath.size() - 1).getArtifact(), dependencyPath);
  }
  
  /**
   * Problem when Maven cannot resolve the artifact at the leaf of {@code dependencyPath} in a
   * dependency tree.
   * 
   * @param artifact dependency that can't be found
   * @param path to the unfound artifact
   */
  public UnresolvableArtifactProblem(Artifact artifact, Collection<DependencyNode> path) {
    super(artifact, path);
  }


  @Override
  public String toString() {
    if (dependencyPath.isEmpty()) {
      return artifact + " was not resolved. Dependency path is unknown.";
    } else {
      return artifact + " was not resolved. Dependency path: " + getPath();
    }
  }
}
