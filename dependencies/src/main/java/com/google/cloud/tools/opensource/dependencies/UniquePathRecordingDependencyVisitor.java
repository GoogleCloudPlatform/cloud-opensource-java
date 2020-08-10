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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that records all paths leading to nodes matching a certain filter criteria.
 * Compared to aether's {@link org.eclipse.aether.util.graph.visitor.PathRecordingDependencyVisitor}
 * , this visits only unique nodes.
 */
final class UniquePathRecordingDependencyVisitor implements DependencyVisitor {

  private final DependencyFilter filter;

  private final List<List<DependencyNode>> paths;

  private final List<DependencyNode> parents;

  private final Set<DependencyNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * Creates a new visitor that uses the specified filter to identify terminal nodes of interesting
   * paths.
   *
   * @param filter The filter used to select terminal nodes of paths to record, may be {@code null}
   *     to match any node.
   */
  public UniquePathRecordingDependencyVisitor(DependencyFilter filter) {
    this.filter = filter;
    paths = new ArrayList<>();
    parents = new ArrayList<>();
  }

  /** Returns the recorded paths, never {@code null}. */
  public ImmutableList<List<DependencyNode>> getPaths() {
    return ImmutableList.copyOf(paths);
  }

  public boolean visitEnter(DependencyNode node) {
    parents.add(node);

    if (filter.accept(node, parents)) {
      paths.add(new ArrayList<>(parents));

      visited.add(node);
      return false;
    }

    // Returning true if this node has not been visited
    return visited.add(node);
  }

  public boolean visitLeave(DependencyNode node) {
    parents.remove(parents.size() - 1);
    return true;
  }
}
