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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

/** Problem in a Maven artifact in a dependency tree. */
public abstract class ArtifactProblem {

  protected final Artifact artifact;

  protected final ImmutableList<DependencyNode> dependencyPath;

  protected ArtifactProblem(Artifact artifact, List<DependencyNode> dependencyPath) {
    this.artifact = checkNotNull(artifact);
    this.dependencyPath = ImmutableList.copyOf(dependencyPath);
  }

  /**
   * Returns the dependency path to the artifact from the root of the dependency tree. An empty list
   * if the path is unknown.
   */
  protected String getPath() {
    return Joiner.on(" > ").join(dependencyPath);
  }

  /** Returns the Maven artifact that has the problem. */
  public Artifact getArtifact() {
    return artifact;
  }
}
