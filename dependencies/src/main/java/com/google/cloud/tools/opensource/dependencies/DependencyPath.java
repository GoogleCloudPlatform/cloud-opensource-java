/*
 * Copyright 2018 Google LLC.
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
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.aether.artifact.Artifact;

/**
 * Path from the root to a node in a dependency tree, where a node is {@link Artifact} with scope
 * and optional flag.
 */
public final class DependencyPath {

  private List<Node> path = new ArrayList<>();

  @VisibleForTesting
  public void add(Artifact artifact, String scope, Boolean optional) {
    path.add(new Node(artifact, scope, optional));
  }

  /** Returns the length of the path. */
  public int size() {
    return path.size();
  }

  /** Returns the artifact in the leaf (the furthest node from the node) of the path. */
  public Artifact getLeaf() {
    return path.get(size() - 1).getArtifact();
  }

  /** Returns the list of artifact in the path. */
  public ImmutableList<Artifact> getArtifacts() {
    return path.stream().map(Node::getArtifact).collect(toImmutableList());
  }

  /**
   * Returns the artifact at {@code i}th node in the path. The {@code 0}th element is the root of
   * the dependency tree.
   */
  public Artifact get(int i) {
    return path.get(i).getArtifact();
  }

  @Override
  public String toString() {
    return Joiner.on(" / ").join(path);
  }
  
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DependencyPath)) {
      return false;
    }
    DependencyPath other = (DependencyPath) o;
    
    if (other.path.size() != path.size()) {
      return false;
    }
    
    for (int i = 0; i < path.size(); i++) {
      Node thisNode = path.get(i);
      Node otherNode = other.path.get(i);
      if (!artifactsEqual(thisNode.getArtifact(), otherNode.getArtifact())) {
        return false; 
      }
      if (!thisNode.getScope().equals(otherNode.getScope())) {
        return false;
      }
      if (thisNode.isOptional() != otherNode.isOptional()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Artifacts are considered to be the same if they have the same group ID, artifact ID, and
   * version.
   *
   * <p>{@link org.eclipse.aether.artifact.DefaultArtifact#equals(Object)} uses {@code file} field,
   * which is unnecessary for our usage.
   */
  private static boolean artifactsEqual(Artifact artifact1, Artifact artifact2) {
    return artifact1.getArtifactId().equals(artifact2.getArtifactId())
        && artifact1.getGroupId().equals(artifact2.getGroupId())
        && artifact1.getVersion().equals(artifact2.getVersion());
  }

  @Override
  public int hashCode() {
    int hashCode = 31;
    for (Node node : path) {
      Artifact artifact = node.getArtifact();
      hashCode =
          37 * hashCode
              + Objects.hash(
                  artifact.getGroupId(),
                  artifact.getArtifactId(),
                  artifact.getVersion(),
                  node.scope,
                  node.optional);
    }
    return hashCode;
  }

  /** Node in a dependency tree, holding scope and optional flag. */
  private static class Node {
    private Artifact artifact;
    private String scope;
    private boolean optional;

    private Node(Artifact artifact, String scope, boolean optional) {
      this.artifact = checkNotNull(artifact);
      this.scope = checkNotNull(scope);
      this.optional = optional;
    }

    private Artifact getArtifact() {
      return artifact;
    }

    private String getScope() {
      return scope;
    }

    private boolean isOptional() {
      return optional;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Node)) {
        return false;
      }
      Node that = (Node) other;
      return optional == that.optional
          && artifact.equals(that.artifact)
          && scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
      return Objects.hash(artifact, scope, optional);
    }

    @Override
    public String toString() {
      String scopeAndOptional = scope + (optional ? ", optional" : "");
      String coordinates = Artifacts.toCoordinates(artifact);
      return String.format("%s (%s)", coordinates, scopeAndOptional);
    }
  }

}
