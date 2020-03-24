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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;

/** Maven artifact entry in a class path. */
class ArtifactClassPathEntry implements ClassPathEntry {
  private Artifact artifact;

  ArtifactClassPathEntry(Artifact artifact) {
    checkNotNull(artifact.getFile());
    this.artifact = artifact;
  }

  @Override
  public String getClassPath() {
    return artifact.getFile().getAbsolutePath();
  }

  Artifact getArtifact() {
    return artifact;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtifactClassPathEntry that = (ArtifactClassPathEntry) o;

    // DefaultArtifact checks equality of
    return Objects.equals(artifact, that.artifact);
  }

  @Override
  public int hashCode() {
    return Objects.hash(artifact);
  }

  @Override
  public String toString() {
    return "Artifact(" + artifact + ")";
  }
}
