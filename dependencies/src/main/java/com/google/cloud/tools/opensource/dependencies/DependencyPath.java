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


import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

/**
 * A sequence of Maven artifacts and dependencies (scope and optional flag) in between.
 *
 * <p>When this represents a path from the root to a leaf of a dependency tree, the first artifact
 * in the path is the root of the tree and the last artifact is the leaf. The sequence of
 * dependencies explains why the leaf is included in the dependency tree.
 *
 * <p>The first node is null for the dependency trees generated for multiple artifacts by {@link
 * DependencyGraphBuilder#buildFullDependencyGraph(List)}; otherwise the root node is not null.
 */
public final class DependencyPath {

  // The root of the dependency path. The project root is not a dependency.
  private final Artifact root;
  // Path without the root
  private final List<Dependency> path = new ArrayList<>();

  public DependencyPath(@Nullable Artifact root) {
    this.root = root;
  }

  @VisibleForTesting
  public DependencyPath append(Dependency dependency) {
    DependencyPath copy = new DependencyPath(root);
    copy.path.addAll(path);
    copy.path.add(dependency);
    return copy;
  }

  public DependencyPath concat(DependencyPath childPath) {
    DependencyPath copy = new DependencyPath(root);
    copy.path.addAll(path);
    copy.path.addAll(childPath.path);
    return copy;
  }

  /** Returns the length of the path. */
  public int size() {
    return path.size() + 1; // including the root
  }

  /** Returns the artifact at the end of the path. */
  public Artifact getLeaf() {
    if (path.isEmpty()) {
      return root;
    } else {
      return path.get(path.size() - 1).getArtifact();
    }
  }

  /** Returns the versionless coordinates of the artifacts in the path. */
  public ImmutableList<String> getArtifactKeys() {
    ImmutableList.Builder<String> builder = ImmutableList.builder();

    if (root != null) {
      builder.add(Artifacts.makeKey(root));
    }
    
    for (Dependency dependency : path) {
      builder.add(Artifacts.makeKey(dependency.getArtifact()));
    }
    
    return builder.build();
  }
  
  /**
   * Returns the artifact at {@code i}th node in the path. The {@code 0}th element is the root of
   * the dependency tree.
   */
  public Artifact get(int i) {
    if (i == 0) {
      return root;
    }
    return path.get(i - 1).getArtifact();
  }

  /**
   * Returns the {@code i}th dependency. As {@link #root} is not a dependency, the {@code 0}th
   * element is the dependency of the root in the path.
   */
  public Dependency getDependency(int i) {
    return path.get(i);
  }

  /**
   * Returns the dependency path of the second to last node in the path. Empty dependency path if
   * the leaf does not have a parent or {@link #path} is empty.
   */
  DependencyPath getParentPath() {
    DependencyPath parent = new DependencyPath(root);
    for (int i = 0; i < path.size() - 1; i++) {
      parent.path.add(path.get(i));
    }
    return parent;
  }

  /**
   * Returns the artifact that declares an exclusion element specified for {@code groupId} and
   * {@code artifactId}. {@code Null} if the dependency path does not have such artifact.
   */
  public Artifact findExclusion(String groupId, String artifactId) {
    Artifact previousArtifact = root;
    for (Dependency dependency : path) {
      for (Exclusion exclusion : dependency.getExclusions()) {
        if (artifactId.equals(exclusion.getArtifactId())
            && groupId.equals(exclusion.getGroupId())) {
          // The exclusion element associated to a dependency is declared by the artifact at
          // one-level above in the dependency path
          return previousArtifact;
        }
      }
      previousArtifact = dependency.getArtifact();
    }
    return null;
  }

  @Override
  public String toString() {
    List<String> formatted =
        path.stream().map(DependencyPath::formatDependency).collect(Collectors.toList());
    StringBuilder builder = new StringBuilder();
    if (root != null) {
      builder.append(root);
      if (!path.isEmpty()) {
        builder.append(" / ");
      }
    }
    builder.append(Joiner.on(" / ").join(formatted));
    return builder.toString();
  }

  private static String formatDependency(Dependency dependency) {
    String scopeAndOptional = dependency.getScope() + (dependency.isOptional() ? ", optional" : "");
    String coordinates = Artifacts.toCoordinates(dependency.getArtifact());
    return String.format("%s (%s)", coordinates, scopeAndOptional);
  }
  
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DependencyPath)) {
      return false;
    }
    DependencyPath other = (DependencyPath) o;

    if (!Objects.equals(other.root, root)) {
      return false;
    }
    if (other.path.size() != path.size()) {
      return false;
    }

    for (int i = 0; i < path.size(); i++) {
      Dependency thisNode = path.get(i);
      Dependency otherNode = other.path.get(i);
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
    for (Dependency node : path) {
      Artifact artifact = node.getArtifact();
      hashCode =
          37 * hashCode
              + Objects.hash(
                  root,
                  artifact.getGroupId(),
                  artifact.getArtifactId(),
                  artifact.getVersion(),
                  node.getScope(),
                  node.isOptional());
    }
    return hashCode;
  }

}
