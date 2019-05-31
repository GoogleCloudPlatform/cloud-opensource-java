/*
 * Copyright 2019 Google LLC.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.eclipse.aether.artifact.Artifact;

public final class Bom {
  
  private final ImmutableList<Artifact> artifacts;
  private final String coordinates;

  /**
   * @param coordinates group:artifact:version
   * @param artifacts the artifacts found in this BOM's managedDependencies section
   */
  @VisibleForTesting
  public Bom(String coordinates, ImmutableList<Artifact> artifacts) {
    this.coordinates = Preconditions.checkNotNull(coordinates);
    if (artifacts == null) {
      artifacts = ImmutableList.of();
    }
    this.artifacts = artifacts;
  }

  /**
   * Returns the artifacts found in this BOM's managedDependencies section.
   */
  public ImmutableList<Artifact> getManagedDependencies() {
    return artifacts;
  }

  /**
   * Returns group:artifact:version
   */
  public String getCoordinates() {
    return coordinates;
  }
}
