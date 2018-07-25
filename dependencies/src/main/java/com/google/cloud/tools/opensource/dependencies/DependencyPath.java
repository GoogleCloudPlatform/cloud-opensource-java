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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;

/**
 * Artifacts are considered to be the same if they have the same group ID,
 * artifact ID, and version.
 */
final class DependencyPath {

  private List<Artifact> path = new ArrayList<>();

  void add(Artifact artifact) {
    path.add(artifact);
  }
  
  @Override
  public String toString() {
    
    StringBuilder builder = new StringBuilder();
    for (Artifact artifact : path) {
      builder.append(Artifacts.toCoordinates(artifact));
      builder.append(" / ");
    }
    String s = builder.toString();
    // chop end
    return s.substring(0, s.length() - 3);
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
  
  private static boolean artifactsEqual(Artifact artifact1, Artifact artifact2) {
    return artifact1.getArtifactId().equals(artifact2.getArtifactId())
        && artifact1.getGroupId().equals(artifact2.getGroupId())
        && artifact1.getVersion().equals(artifact2.getVersion());
  }

  int size() {
    return path.size();
  }

  Artifact getLeaf() {
    return path.get(size() - 1);
  }

}
