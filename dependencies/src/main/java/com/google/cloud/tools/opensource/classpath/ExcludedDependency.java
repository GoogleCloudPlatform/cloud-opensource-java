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
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;

/** An dependency is not supplied because {@code excludingArtifact} excludes the dependency. */
class ExcludedDependency extends MissingDependency {

  /**
   * The path from {@code LinkageProblem.sourceClass}'s artifact to an artifact that contains a
   * valid symbol.
   */
  private DependencyPath pathToMissingArtifact;

  private Artifact excludingArtifact;

  ExcludedDependency(DependencyPath pathToMissingArtifact, Artifact excludingArtifact) {
    super(pathToMissingArtifact);
    this.pathToMissingArtifact = pathToMissingArtifact;
    this.excludingArtifact = excludingArtifact;
  }

  DependencyPath getPathToMissingArtifact() {
    return pathToMissingArtifact;
  }

  /** Returns the artifact that declares the exclusion of the missing artifact. */
  Artifact getExcludingArtifact() {
    return excludingArtifact;
  }

  @Override
  public String toString() {
    Artifact artifactContainingValidSymbol = pathToMissingArtifact.getLeaf();
    return "The valid symbol is in "
        + artifactContainingValidSymbol
        + " at "
        + pathToMissingArtifact
        + " but it was not selected because "
        + Artifacts.toCoordinates(excludingArtifact)
        + " excludes "
        + Artifacts.makeKey(artifactContainingValidSymbol)
        + ".";
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ExcludedDependency that = (ExcludedDependency) other;
    return Objects.equals(pathToMissingArtifact, that.pathToMissingArtifact)
        && Objects.equals(excludingArtifact, that.excludingArtifact);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pathToMissingArtifact, excludingArtifact);
  }
}
