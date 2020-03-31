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


import com.google.cloud.tools.opensource.dependencies.Artifacts;
import javax.annotation.Nullable;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/** Matcher on the classes in Maven Artifacts. */
class ArtifactMatcher implements SymbolProblemTargetMatcher, SymbolProblemSourceMatcher {

  // Group ID, artifact ID, and version
  private final String coordinates;

  ArtifactMatcher(String coordinates) {
    // Relies on DefaultArtifact to validate the input
    this.coordinates = Artifacts.toCoordinates(new DefaultArtifact(coordinates));
  }

  @Override
  public boolean match(SymbolProblem symbolProblem) {
    ClassFile classFile = symbolProblem.getContainingClass();
    return matchClassFile(classFile);
  }

  @Override
  public boolean match(ClassFile source) {
    return matchClassFile(source);
  }

  private boolean matchClassFile(@Nullable ClassFile classFile) {
    if (classFile == null) {
      return false;
    }
    ClassPathEntry entry = classFile.getClassPathEntry();
    Artifact artifact = entry.getArtifact();
    if (artifact == null) {
      return false;
    }
    return coordinates.equals(Artifacts.toCoordinates(artifact));
  }
}
