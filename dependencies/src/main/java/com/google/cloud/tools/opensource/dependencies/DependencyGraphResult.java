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

import com.google.common.collect.ImmutableList;

/** Result of dependency graph building with {@link UnresolvableArtifactProblem}s if any. */
public final class DependencyGraphResult {

  private final DependencyGraph dependencyGraph;
  private final ImmutableList<UnresolvableArtifactProblem> artifactProblems;

  DependencyGraphResult(
      DependencyGraph dependencyGraph, Iterable<UnresolvableArtifactProblem> artifactProblems) {
    this.dependencyGraph = checkNotNull(dependencyGraph);
    this.artifactProblems = ImmutableList.copyOf(artifactProblems);
  }

  public DependencyGraph getDependencyGraph() {
    return dependencyGraph;
  }

  /** Returns problems encountered while constructing the dependency graph. */
  public ImmutableList<UnresolvableArtifactProblem> getArtifactProblems() {
    return artifactProblems;
  }
}
