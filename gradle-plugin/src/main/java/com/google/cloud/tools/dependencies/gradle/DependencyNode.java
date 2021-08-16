/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.dependencies.gradle;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.result.ResolvedComponentResult;

/** Dependency nodes to record dependency paths while traversing the dependency tree */
class DependencyNode {
  ResolvedComponentResult componentResult;

  DependencyNode parent;

  DependencyNode(
      ResolvedComponentResult componentResult, DependencyNode parent) {
    this.componentResult = componentResult;
    this.parent = parent;
  }

   boolean isDescendantOf(ResolvedComponentResult other) {
    if (componentResult.equals(other)) {
      return true;
    }
    if (parent == null) {
      return false;
    }
    return parent.isDescendantOf(other);
  }

   String pathFromRoot() {
     return rootToNode().stream().map(LinkageCheckTask::formatComponentResult)
         .collect(Collectors.joining(" / "));
  }

  ImmutableList<ResolvedComponentResult> rootToNode() {
    ArrayDeque<ResolvedComponentResult> nodes = new ArrayDeque<>();
    for (DependencyNode iter = this; iter != null; iter = iter.parent) {
      nodes.addFirst(iter.componentResult);
    }
    return ImmutableList.copyOf(nodes);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("componentResult", componentResult)
        .add("parent", parent)
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DependencyNode that = (DependencyNode) other;
    return Objects.equals(componentResult, that.componentResult)
        && Objects.equals(parent, that.parent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(componentResult, parent);
  }
}
