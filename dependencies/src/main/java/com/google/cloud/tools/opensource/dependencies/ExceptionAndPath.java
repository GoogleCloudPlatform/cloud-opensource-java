/*
 * Copyright 2019 Google LLC.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Tuple of a path in a Maven dependency graph and a {@link RepositoryException}, indicating that
 * the {@link DependencyNode} specified at the path raises the exception.
 */
@AutoValue
public abstract class ExceptionAndPath {

  /** Returns a path from the root of dependency graph to a dependency node. */
  public abstract ImmutableList<DependencyNode> getPath();

  /** Returns the exception raised at the {@link DependencyNode} specified at the path. */
  public abstract RepositoryException getException();

  static ExceptionAndPath create(
      Iterable<DependencyNode> parentDependencyNodes,
      DependencyNode dependencyNode,
      RepositoryException repositoryException) {
    return builder()
        .setPath(
            ImmutableList.<DependencyNode>builder()
                .addAll(parentDependencyNodes)
                .add(dependencyNode)
                .build())
        .setException(repositoryException)
        .build();
  }

  private static Builder builder() {
    return new AutoValue_ExceptionAndPath.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPath(Iterable<DependencyNode> path);

    public abstract Builder setException(RepositoryException exception);

    public abstract ExceptionAndPath build();
  }
}
