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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.aether.RepositoryException;
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

  // Finding latest version requires metadata from remote repository
  private final RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();

  public static void main(String[] arguments)
      throws RepositoryException, IOException, LinkageMonitorException {
    if (arguments.length < 1 || arguments[0].split(":").length != 2) {
      System.err.println(
          "Please specify BOM coordinates without version. Example:"
              + " com.google.cloud:libraries-bom");
      System.exit(1);
    }
    String bomCoordinates = arguments[0];
    List<String> coordinatesElements = Splitter.on(':').splitToList(bomCoordinates);

    new LinkageMonitor().run(coordinatesElements.get(0), coordinatesElements.get(1));
  }

  private void run(String groupId, String artifactId)
      throws RepositoryException, IOException, LinkageMonitorException {
    String latestBomCoordinates = findLatestCoordinates(groupId, artifactId);
    System.out.println("BOM Coordinates: " + latestBomCoordinates);
    Bom baseline = RepositoryUtility.readBom(latestBomCoordinates);
    ImmutableSet<SymbolProblem> problemsInBaseline =
        LinkageChecker.create(baseline).findSymbolProblems().keySet();
    Bom snapshot = copyWithSnapshot(repositorySystem, baseline);

    // Compare coordinates of the two BOMs. No need to run comparison if they are the same.
    ImmutableList<String> baselineCoordinates =
        baseline.getManagedDependencies().stream()
            .map(Artifacts::toCoordinates) // DefaultArtifact does not override equals
            .collect(toImmutableList());
    ImmutableList<String> snapshotCoordinates =
        snapshot.getManagedDependencies().stream()
            .map(Artifacts::toCoordinates)
            .collect(toImmutableList());
    if (baselineCoordinates.equals(snapshotCoordinates)) {
      System.out.println(
          "The content of the snapshot BOM and the original BOM are the same. Not running"
              + " comparison.");
      return;
    }

    ImmutableSet<SymbolProblem> problemsInSnapshot =
        LinkageChecker.create(snapshot).findSymbolProblems().keySet();

    if (problemsInBaseline.equals(problemsInSnapshot)) {
      System.out.println(
          "Snapshot versions have the same " + problemsInBaseline.size() + " errors as baseline");
      return;
    }

    Set<SymbolProblem> fixedErrors = Sets.difference(problemsInBaseline, problemsInSnapshot);
    if (!fixedErrors.isEmpty()) {
      System.out.println("The following errors in the baseline no longer appear in the snapshot:");
      System.out.println(fixedErrors);
    }
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
      managedDependencies.add(
          findSnapshotVersion(repositorySystem, session, managedDependency)
              .map(managedDependency::setVersion)
              .orElse(managedDependency));
    }
    // "-SNAPSHOT" suffix for coordinate to distinguish easily.
    return new Bom(bom.getCoordinates() + "-SNAPSHOT", managedDependencies.build());
  }

  /**
   * Returns {@code Optional} describing the highest snapshot version if such version is available
   * in {@code repositorySystem}; otherwise an empty {@code Optional}.
   */
  private static Optional<String> findSnapshotVersion(
      RepositorySystem repositorySystem, RepositorySystemSession session, Artifact artifact)
      throws VersionRangeResolutionException {
    String version =
        findHighestVersion(
            repositorySystem, session, artifact.getGroupId(), artifact.getArtifactId());
    if (version.contains("-SNAPSHOT")) {
      return Optional.of(version);
    }
    return Optional.empty();
  }

  private static String findHighestVersion(
      RepositorySystem repositorySystem,
      RepositorySystemSession session,
      String groupId,
      String artifactId)
      throws VersionRangeResolutionException {
    Artifact artifactWithVersionRange = new DefaultArtifact(groupId, artifactId, null, "(0,]");
    VersionRangeRequest request =
        new VersionRangeRequest(
            artifactWithVersionRange, ImmutableList.of(RepositoryUtility.CENTRAL), null);
    VersionRangeResult versionResult = repositorySystem.resolveVersionRange(session, request);

    Verify.verify(versionResult.getHighestVersion() != null, "Highest version should not be null");
    return versionResult.getHighestVersion().toString();
  }

  private String findLatestCoordinates(String groupId, String artifactId)
      throws VersionRangeResolutionException {
    RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);
    String highestVersion = findHighestVersion(repositorySystem, session, groupId, artifactId);
    return String.format("%s:%s:%s", groupId, artifactId, highestVersion);
  }
}
