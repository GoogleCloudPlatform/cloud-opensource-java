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
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;

/**
 * Diamond dependency conflict caused a {@link LinkageProblem} where the {@link LinkageProblem}'s
 * invalid reference points to the symbol in {@code pathToSelectedArtifact.getLeaf()} but a valid
 * symbol is in {@code pathToUnselectedArtifact.getLeaf()}.
 */
class DependencyConflict extends LinkageProblemCause {
  private Symbol symbol;
  private DependencyPath pathToUnselectedArtifact;
  private DependencyPath pathToSelectedArtifact;

  DependencyConflict(
      Symbol symbol,
      DependencyPath pathToSelectedArtifact,
      DependencyPath pathToUnselectedArtifact) {
    this.symbol = checkNotNull(symbol);
    this.pathToUnselectedArtifact = checkNotNull(pathToUnselectedArtifact);
    this.pathToSelectedArtifact = checkNotNull(pathToSelectedArtifact);
  }

  public DependencyPath getPathToUnselectedArtifact() {
    return pathToUnselectedArtifact;
  }

  public DependencyPath getPathToSelectedArtifact() {
    return pathToSelectedArtifact;
  }

  @Override
  public String toString() {
    Artifact selected = pathToSelectedArtifact.getLeaf();
    Artifact unselected = pathToUnselectedArtifact.getLeaf();
    return "Dependency conflict: "
        + Artifacts.toCoordinates(selected)
        + " (selected for the class path) does not have the symbol \""
        + symbol
        + "\" but "
        + Artifacts.toCoordinates(unselected)
        + " (unselected) defines it.\n"
        + "  selected: "
        + pathToSelectedArtifact
        + "\n  unselected: "
        + pathToUnselectedArtifact;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DependencyConflict that = (DependencyConflict) other;
    return Objects.equals(symbol, that.symbol)
        && Objects.equals(pathToUnselectedArtifact, that.pathToUnselectedArtifact)
        && Objects.equals(pathToSelectedArtifact, that.pathToSelectedArtifact);
  }

  @Override
  public int hashCode() {
    return Objects.hash(symbol, pathToUnselectedArtifact, pathToSelectedArtifact);
  }
}
