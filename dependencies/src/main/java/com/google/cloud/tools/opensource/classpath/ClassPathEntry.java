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

import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;

/** JAR file entry in a class path. */
class ClassPathEntry {
  private Path jar;

  ClassPathEntry(Path jar) {
    this.jar = checkNotNull(jar);
  }

  /** Returns a path of the entry. */
  String getPath() {
    return jar.toAbsolutePath().toString();
  }

  /**
   * Returns Maven artifact associated for the JAR file. If the JAR file does not have an artifact,
   * {@code null}.
   */
  Artifact getArtifact() {
    return null;
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
    return Objects.equals(jar, that.jar);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jar);
  }

  @Override
  public String toString() {
    return "JAR(" + jar + ")";
  }
}
