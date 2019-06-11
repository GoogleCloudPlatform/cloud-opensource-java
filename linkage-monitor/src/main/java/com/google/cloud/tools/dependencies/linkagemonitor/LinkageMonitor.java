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

import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

/**
 * Linkage Monitor detects new linkage errors caused by locally-installed snapshot artifacts for a
 * BOM (bill-of-materials).
 */
public class LinkageMonitor {

  // Finding latest version requires metadata from remote repository
  private final RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();

  public static void main(String[] arguments)
      throws RepositoryException, IOException, LinkageMonitorException {
    if (arguments.length < 1) {
      System.err.println(
          "Please specify BOM coordinates. Example: com.google.cloud:libraries-bom:1.2.1");
      System.exit(1);
    }
    String bomCoordinates = arguments[0];
    new LinkageMonitor().run(bomCoordinates);
  }

  private void run(String bomCoordinates)
      throws RepositoryException, IOException, LinkageMonitorException {
    Bom baseline = RepositoryUtility.readBom(bomCoordinates);
    ImmutableSet<SymbolProblem> problemsInBaseline =
        LinkageChecker.create(baseline).findSymbolProblems().keySet();

    Bom snapshot = copyWithSnapshot(repositorySystem, baseline);

    ImmutableSet<SymbolProblem> problemsInSnapshot =
        LinkageChecker.create(snapshot).findSymbolProblems().keySet();

    Set<SymbolProblem> newErrors = Sets.difference(problemsInSnapshot, problemsInBaseline);
    if (!newErrors.isEmpty()) {
      // TODO(#683): Display new linkage errors caused by snapshot versions if any
      System.err.println("There are one or more new new linkage errors in snapshot versions:");
      System.err.println(newErrors);
      int errorSize = newErrors.size();
      throw new LinkageMonitorException(
          String.format("Found %d new linkage error%s", errorSize, errorSize > 1 ? "s" : ""));
    }
    // No new symbol problems introduced by snapshot BOM. Returning success.
  }

  /**
   * Returns a copy of {@code bom} replacing its managed dependencies that have locally-installed
   * snapshot versions.
   */
  @VisibleForTesting
  static Bom copyWithSnapshot(RepositorySystem repositorySystem, Bom bom)
      throws VersionRangeResolutionException {
    ImmutableList.Builder<Artifact> managedDependencies = ImmutableList.builder();
    RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);

    for (Artifact managedDependency : bom.getManagedDependencies()) {
      String snapshotVersion = findSnapshotVersion(repositorySystem, session, managedDependency);
      if (snapshotVersion == null) {
        managedDependencies.add(managedDependency);
      } else {
        managedDependencies.add(managedDependency.setVersion(snapshotVersion));
      }
    }
    // "-SNAPSHOT" suffix for coordinate to distinguish easily.
    return new Bom(bom.getCoordinates() + "-SNAPSHOT", managedDependencies.build());
  }

  /**
   * Returns the highest snapshot version installed in {@code repositorySystem}. Null if highest
   * version is not a snapshot.
   */
  @VisibleForTesting
  static String findSnapshotVersion(
      RepositorySystem repositorySystem, RepositorySystemSession session, Artifact artifact)
      throws VersionRangeResolutionException {
    Artifact artifactWithVersionRange = artifact.setVersion("(0,]");
    VersionRangeRequest request =
        new VersionRangeRequest(
            artifactWithVersionRange, ImmutableList.of(RepositoryUtility.CENTRAL), null);
    VersionRangeResult versionResult = repositorySystem.resolveVersionRange(session, request);

    Verify.verify(versionResult.getHighestVersion() != null, "Highest version should not be null");
    String version = versionResult.getHighestVersion().toString();
    if (version.contains("-SNAPSHOT")) {
      return version;
    }
    return null;
  }
}
