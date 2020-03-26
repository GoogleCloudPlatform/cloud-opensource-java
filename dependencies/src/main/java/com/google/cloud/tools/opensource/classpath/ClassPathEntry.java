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

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/** An entry in a class path. */
public final class ClassPathEntry {

  private Path jar;
  private Artifact artifact;

  /** An entry for a JAR file without association with a Maven artifact. */
  ClassPathEntry(Path jar) {
    this.jar = checkNotNull(jar);
  }

  /** An entry for a Maven artifact. */
  public ClassPathEntry(Artifact artifact) {
    checkNotNull(artifact.getFile());
    this.artifact = artifact;
  }

  /** Returns the path of the entry. */
  String getPath() {
    if (artifact != null) {
      return artifact.getFile().toString();
    } else {
      return jar.toString();
    }
  }

  /**
   * Returns Maven artifact associated with the JAR file. If the JAR file does not have an artifact,
   * {@code null}.
   */
  Artifact getArtifact() {
    return artifact;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ClassPathEntry that = (ClassPathEntry) other;
    return Objects.equals(jar, that.jar) && Objects.equals(artifact, that.artifact);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jar, artifact);
  }

  @Override
  public String toString() {
    if (artifact != null) {
      // Group ID, artifact ID and version. No extension such as "jar" or "tar.gz", because Linkage
      // Checker uses only JAR artifacts.
      return Artifacts.toCoordinates(artifact);
    } else {
      return jar.toString();
    }
  }

  @VisibleForTesting
  public static ClassPathEntry of(String coordinates, String filePath) {
    Artifact artifact = new DefaultArtifact(coordinates);
    return new ClassPathEntry(artifact.setFile(new File(filePath)));
  }
}
