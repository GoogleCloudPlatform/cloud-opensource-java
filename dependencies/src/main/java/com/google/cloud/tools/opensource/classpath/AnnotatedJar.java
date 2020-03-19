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
import javax.annotation.Nullable;
import org.eclipse.aether.artifact.Artifact;

/** JAR file annotated with an optional Maven artifact. */
final class AnnotatedJar {
  private Path jar;

  private Artifact artifact;

  AnnotatedJar(Path jar, @Nullable Artifact artifact) {
    this.jar = checkNotNull(jar);
    this.artifact = artifact;
  }

  AnnotatedJar(Path jar) {
    this(jar, null);
  }

  /**
   * The Maven artifact that holds the JAR file, if the JAR is downloaded for a Maven artifact.
   * {@code Null} if the JAR file is not from a Maven artifact.
   */
  @Nullable
  Artifact getArtifact() {
    return artifact;
  }

  /** Path to the JAR file. */
  Path getJar() {
    return jar;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    AnnotatedJar that = (AnnotatedJar) other;
    return Objects.equals(jar, that.jar) && Objects.equals(artifact, that.artifact);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jar, artifact);
  }
}
