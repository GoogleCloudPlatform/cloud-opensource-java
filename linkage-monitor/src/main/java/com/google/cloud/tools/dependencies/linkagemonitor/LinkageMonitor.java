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

package com.google.cloud.tools.dependencies.linkagemonitor;

import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

/**
 * Linkage Monitor detects new linkage errors caused by locally-installed snapshot artifacts for a
 * BOM (bill-of-materials).
 */
public class LinkageMonitor {

  public static void main(String[] arguments) throws VersionRangeResolutionException {

    RepositorySystem repositorySystem = RepositoryUtility.newFileRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);

    Artifact artifact = new DefaultArtifact("com.google.cloud.tools:dependencies:0.2.1");
    String version = findSnapshotVersion(repositorySystem, session, artifact);
    System.out.println(version);
    if (arguments.length < 1) {
      System.err.println(
          "Please specify BOM coordinates. Example: com.google.cloud:libraries-bom:1.2.1");
      System.exit(1);
    }
    String bomCoordinates = arguments[0];
    System.out.println("Linkage Monitor for " + bomCoordinates);
    // TODO(#681): Run Linkage Checker for the BOM specified in argument
    // TODO(#682): Copy the BOM with locally-installed snapshot versions
    // TODO(#683): Display new linkage errors caused by snapshot versions if any
  }

  /**
   * Returns the highest snapshot version installed in {@code repositorySystem}. Null if highest
   * version is not a snapshot.
   */
  private static String findSnapshotVersion(
      RepositorySystem repositorySystem, RepositorySystemSession session, Artifact artifact)
      throws VersionRangeResolutionException {
    Artifact artifactWithVersionRange = artifact.setVersion("(0,]");
    VersionRangeRequest request = new VersionRangeRequest(artifactWithVersionRange, null, null);
    VersionRangeResult versionResult = repositorySystem.resolveVersionRange(session, request);
    String version = versionResult.getHighestVersion().toString();
    if (version.contains("-SNAPSHOT")) {
      return version;
    }
    return null;
  }
}
