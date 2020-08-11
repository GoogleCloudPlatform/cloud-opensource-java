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

import java.util.Objects;

/**
 * A path to a node in a {@link DependencyGraph}.
 *
 * @param <T> The type of the graph node. It's {@code org.eclipse.aether.graph.DependencyNode} for
 *     Maven's graph and {@code org.gradle.api.artifacts.ResolvedDependency} for Gradle's graph.
 */
public final class PathToNode<T> {
  private final T node;

  // This may be null for the root
  private final DependencyPath parentPath;

  /** Returns the node in the graph traversal. */
  public T getNode() {
    return node;
  }

  /** Returns the path from the root of the graph to the parent of the node. */
  public DependencyPath getParentPath() {
    return parentPath;
  }

  public PathToNode(T node, DependencyPath parentPath) {
    this.node = node;
    this.parentPath = parentPath;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    PathToNode<?> that = (PathToNode<?>) other;
    return Objects.equals(node, that.node) && Objects.equals(parentPath, that.parentPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(node, parentPath);
  }
}
