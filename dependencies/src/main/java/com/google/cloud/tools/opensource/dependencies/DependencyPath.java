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

import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;

import java.util.stream.Collectors;
import org.eclipse.aether.artifact.Artifact;

/**
 * A representation of a dependency path from a root Artifact to a node Artifact.
 */
public final class DependencyPath {

  private List<Artifact> path = new ArrayList<>();

  void add(Artifact artifact) {
    path.add(artifact);
  }
  
  @Override
  public String toString() {
    return Joiner.on(" / ")
        .join(path.stream().map(Artifacts::toCoordinates).collect(Collectors.toList()));
  }
  
  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof DependencyPath)) {
      return false;
    }
    DependencyPath other = (DependencyPath) o;
    
    if (other.path.size() != path.size()) {
      return false;
    }
    
    for (int i = 0; i < path.size(); i++) {
      if (!artifactsEqual(path.get(i), other.path.get(i))) {
        return false; 
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 31;
    for (Artifact artifact : path) {
      hashCode = 37 * hashCode
          + (artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion())
              .hashCode();
    }
    return hashCode; 
  }
  
  /**
   * Artifacts are considered to be the same if they have the same group ID,
   * artifact ID, and version.
   */
  private static boolean artifactsEqual(Artifact artifact1, Artifact artifact2) {
    return artifact1.getArtifactId().equals(artifact2.getArtifactId())
        && artifact1.getGroupId().equals(artifact2.getGroupId())
        && artifact1.getVersion().equals(artifact2.getVersion());
  }

  public int size() {
    return path.size();
  }

  public Artifact getLeaf() {
    return path.get(size() - 1);
  }

  public List<Artifact> getPath() {
    return path;
  }

  // TODO think about index out of bounds
  public Artifact get(int i) {
    return path.get(i);
  }

}
