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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Tuple of a path in a Maven dependency graph and a {@link RepositoryException}, indicating that
 * the {@link DependencyNode} specified at the path raises the exception.
 */
public final class ExceptionAndPath {

  private final ImmutableList<DependencyNode> path;

  private final RepositoryException exception;

  /** Returns a path from the root of dependency graph to a dependency node. */
  public ImmutableList<DependencyNode> getPath() {
    return path;
  }

  /** Returns the exception raised at the {@link DependencyNode} specified at the path. */
  RepositoryException getException() {
    return exception;
  }

  private ExceptionAndPath(ImmutableList<DependencyNode> path, RepositoryException exception) {
    this.path = checkNotNull(path);
    this.exception = checkNotNull(exception);
  }

  static ExceptionAndPath create(
      Iterable<DependencyNode> parentDependencyNodes,
      DependencyNode dependencyNode,
      RepositoryException repositoryException) {
    return new ExceptionAndPath(
        ImmutableList.<DependencyNode>builder()
            .addAll(parentDependencyNodes)
            .add(dependencyNode)
            .build(),
        repositoryException);
  }

  // Regarding equality of this instance, equals and hashCode are not needed as long as this
  // instance is only used in a list. RepositoryException and DefaultDependencyNode do not
  // implement equals methods and no hashCode.
}
