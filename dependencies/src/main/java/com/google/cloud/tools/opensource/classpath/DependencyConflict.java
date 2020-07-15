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

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import java.util.Objects;

/**
 * Diamond dependency conflict caused the {@link LinkageProblem} where whe {@link LinkageProblem}'s
 * invalid reference points to the symbol in {@code pathToSelectedArtifact} but a valid symbol is in
 * {@code pathToUnselectedArtifactFromSource}.
 */
class DependencyConflict extends LinkageProblemCause {
  DependencyPath pathToUnselectedArtifact;
  DependencyPath pathToSelectedArtifact;

  DependencyConflict(
      DependencyPath pathToSelectedArtifact, DependencyPath pathToUnselectedArtifactFromSource) {
    this.pathToUnselectedArtifact = checkNotNull(pathToUnselectedArtifactFromSource);
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
    return "Dependency conflict: '"
        + pathToSelectedArtifact
        + "' is selected but the unselected '"
        + pathToUnselectedArtifact
        + "' has a valid symbol";
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
    return Objects.equals(pathToUnselectedArtifact, that.pathToUnselectedArtifact)
        && Objects.equals(pathToSelectedArtifact, that.pathToSelectedArtifact);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pathToUnselectedArtifact, pathToSelectedArtifact);
  }
}
