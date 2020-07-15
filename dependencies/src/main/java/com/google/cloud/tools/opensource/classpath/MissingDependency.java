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
import org.eclipse.aether.artifact.Artifact;

/** An optional or provided-scope dependency is not supplied. */
class MissingDependency extends LinkageProblemCause {

  /**
   * The path from {@code LinkageProblem.sourceClass}'s artifact to an artifact that contains a
   * valid symbol.
   */
  private DependencyPath pathToMissingArtifact;

  MissingDependency(DependencyPath pathToMissingArtifact) {
    this.pathToMissingArtifact = checkNotNull(pathToMissingArtifact);
  }

  DependencyPath getPathToMissingArtifact() {
    return pathToMissingArtifact;
  }

  @Override
  public String toString() {
    Artifact artifactContainingValidSymbol = pathToMissingArtifact.getLeaf();
    return "The valid symbol is in "
        + artifactContainingValidSymbol
        + " at "
        + pathToMissingArtifact;
  }
}
