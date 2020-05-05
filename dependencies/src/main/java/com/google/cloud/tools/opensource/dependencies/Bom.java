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
import com.google.common.collect.ImmutableSet;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;

public final class Bom {
  
  private static final ImmutableSet<String> BOM_SKIP_ARTIFACT_IDS =
      ImmutableSet.of("google-cloud-logging-logback", "google-cloud-contrib");

  
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
   * Returns group:artifact:version.
   */
  public String getCoordinates() {
    return coordinates;
  }

  /** Returns true if the {@code artifact} in BOM should be skipped for checks. */
  public static boolean shouldSkipBomMember(Artifact artifact) {
    if ("testlib".equals(artifact.getClassifier())) {
      // we don't report on test libraries
      return true;
    }
  
    String type = artifact.getProperty(ArtifactProperties.TYPE, "jar");
    if ("test-jar".equals(type)) {
      return true;
    }
  
    // TODO remove this hack once we get these out of google-cloud-java's BOM
    if (BOM_SKIP_ARTIFACT_IDS.contains(artifact.getArtifactId())) {
      return true;
    }
  
    return false;
  }
}
