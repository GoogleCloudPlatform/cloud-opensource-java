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

package com.google.cloud.tools.opensource.classpath;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;

/**
 * Retain only the first version of a groupId:artifactId encountered.
 */
class DependencyMediation {
  
  private List<Artifact> artifacts = new ArrayList<>();

  void put(Artifact artifact) {    
    if (artifacts.stream().map(Artifacts::makeKey)
        .anyMatch(key -> Artifacts.makeKey(artifact).equals(key))) {
      return;
    }
    
    artifacts.add(artifact);
  }

  /**
   * Returns true iff dependency mediation will select this artifact.
   */
  // TODO might be a problem if there's a classifier
  boolean selects(Artifact artifact) {
    return artifacts.contains(artifact);
  }

  void put(DependencyPath dependencyPath) {
    Artifact artifact = dependencyPath.getLeaf();
    File file = artifact.getFile();
    if (file == null) {
      return;
    }
    Path jarAbsolutePath = file.toPath().toAbsolutePath();
    if (!jarAbsolutePath.toString().endsWith(".jar")) {
      return;
    } 
    put(artifact);
  }
}