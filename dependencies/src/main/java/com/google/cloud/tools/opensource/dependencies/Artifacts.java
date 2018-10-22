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

import org.eclipse.aether.artifact.Artifact;

/**
 * Common utilities for operating on {@code org.eclipse.aether.artifact.Artifact} objects.
 */
public class Artifacts {

  /**
   * Returns the artifact's Maven coordinates in the form groupId:artifactId:version.
   * Repo and packaging are not included.
   */
  public static String toCoordinates(Artifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
  }

  public static String makeKey(Artifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId();
  }

}
