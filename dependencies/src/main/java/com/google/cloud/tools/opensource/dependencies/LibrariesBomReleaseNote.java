/*
 * Copyright 2022 Google LLC.
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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

/** Tool to generate the changes in the content of the BOM since the previous release. */
class LibrariesBomReleaseNote {

  private static final RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();
  private static final VersionComparator versionComparator = new VersionComparator();
  private static final Splitter dotSplitter = Splitter.on(".");
  private static final String cloudLibraryArtifactPrefix = "com.google.cloud:google-cloud-";
  private static final StringBuilder report = new StringBuilder();

  private static boolean clientLibraryFilter(String coordinates) {
    if (coordinates.contains("google-cloud-core")) {
      // Google Cloud Core is reported in core Google library section as this is not meant to be
      // used by customers directly.
      return false;
    }
    if (coordinates.startsWith(cloudLibraryArtifactPrefix)) {
      return true;
    }
    if (coordinates.startsWith("com.google.cloud:google-")) {
      // google-iam-admin has special group ID because it's not just for Cloud
      return true;
    }
    // proto- and grpc- artifacts are not meant to be used by the customers directly.
    return false;
  }

  public static void main(String[] arguments)
      throws ArtifactDescriptorException, MavenRepositoryException {
    if (arguments.length != 1) {
      System.out.println(
          "Please provide BOM coordinates or file path. For example,"
              + " com.google.cloud:libraries-bom:25.0.0");
      System.exit(1);
    }
    String bomCoordinatesOrFile = arguments[0];

    int splitByColonSize = Splitter.on(":").splitToList(bomCoordinatesOrFile).size();
    Bom bom =
        (splitByColonSize == 3 || splitByColonSize == 4)
            ? Bom.readBom(bomCoordinatesOrFile)
            : Bom.readBom(Paths.get(bomCoordinatesOrFile));

    Bom previousBom = previousBom(bom);

    DefaultArtifact bomArtifact = new DefaultArtifact(bom.getCoordinates());
    report.append("GCP Libraries BOM " + bomArtifact.getVersion() + "\n\n");

    printCloudClientBomDifference(previousBom, bom);
    report.append("\n");
    printKeyCoreLibraryDependencies(bom);
    report.append("\n");
    printApiReferenceLink();

    report.append("\n\n\n\n");
    System.out.println(report);
  }

  private static void printKeyCoreLibraryDependencies(Bom bom) {
    Map<String, String> versionlessCoordinatesToVersion = createVersionLessCoordinatesToKey(bom);
    report.append("# Core Library Dependencies\n");
    report.append("These client libraries are built with the following Java libraries:\n");
    report
        .append("- Guava: ")
        .append(versionlessCoordinatesToVersion.get("com.google.guava:guava"))
        .append("\n");
    report
        .append("- Protobuf Java: ")
        .append(versionlessCoordinatesToVersion.get("com.google.protobuf:protobuf-java"))
        .append("\n");
    report
        .append("- Google Auth Library: ")
        .append(
            versionlessCoordinatesToVersion.get("com.google.auth:google-auth-library-credentials"))
        .append("\n");
    report
        .append("- gRPC: ")
        .append(versionlessCoordinatesToVersion.get("io.grpc:grpc-api"))
        .append("\n");
    report
        .append("- GAX: ")
        .append(versionlessCoordinatesToVersion.get("com.google.api:gax"))
        .append("\n");
    report
        .append("- Google Cloud Core: ")
        .append(versionlessCoordinatesToVersion.get("com.google.cloud:google-cloud-core"))
        .append("\n");
  }

  private static void printApiReferenceLink() {
    report.append("# API Reference\n");
    report.append(
        "You can find the API references of the SDK in [Java Cloud Client Libraries]"
            + "(https://cloud.google.com/java/docs/reference)\n");
  }

  /**
   * Returns the BOM that was released prior to the {@code bom}, asking Maven repositories for
   * available versions.
   */
  private static Bom previousBom(Bom bom)
      throws MavenRepositoryException, ArtifactDescriptorException {
    String coordinates = bom.getCoordinates();
    DefaultArtifact bomArtifact = new DefaultArtifact(coordinates);

    // The highest version comes last.
    ImmutableList<String> versions =
        RepositoryUtility.findVersions(
            repositorySystem, bomArtifact.getGroupId(), bomArtifact.getArtifactId());

    Verify.verify(
        !versions.isEmpty(),
        "The versions returned empty. "
            + "Please check the coordinates (groupId: %s, artifactId: %s)",
        bomArtifact.getGroupId(),
        bomArtifact.getArtifactId());

    String previousVersionCandidate = versions.get(0);
    for (String version : versions) {
      if (versionComparator.compare(version, bomArtifact.getVersion()) >= 0) {
        break;
      }
      if (version.contains("-SNAPSHOT")) {
        // Ignore the artifacts from local Maven repository
        continue;
      }
      previousVersionCandidate = version;
    }

    return Bom.readBom(bomArtifact.setVersion(previousVersionCandidate).toString());
  }

  private static ImmutableMap<String, String> createVersionLessCoordinatesToKey(Bom bom) {
    Map<String, String> versionLessCoordinatesToVersion = new HashMap<>();
    List<Artifact> managedDependencies = new ArrayList(bom.getManagedDependencies());

    // Sort alphabetical order based on the Maven coordinates
    managedDependencies.sort(
        (artifact1, artifact2) -> artifact1.toString().compareTo(artifact2.toString()));
    for (Artifact managedDependency : managedDependencies) {
      String versionlessCoordinates = Artifacts.makeKey(managedDependency);
      versionLessCoordinatesToVersion.put(versionlessCoordinates, managedDependency.getVersion());
    }
    return ImmutableMap.copyOf(versionLessCoordinatesToVersion);
  }

  private static void printCloudClientBomDifference(Bom oldBom, Bom newBom)
      throws MavenRepositoryException {
    Map<String, String> versionlessCoordinatesToVersionOld =
        createVersionLessCoordinatesToKey(oldBom);
    Map<String, String> versionlessCoordinatesToVersionNew =
        createVersionLessCoordinatesToKey(newBom);

    ImmutableSet<String> cloudLibrariesVersionlessCoordinatesInNew =
        versionlessCoordinatesToVersionNew.keySet().stream()
            .filter(LibrariesBomReleaseNote::clientLibraryFilter)
            .collect(ImmutableSet.toImmutableSet());
    ImmutableSet<String> cloudLibrariesVersionlessCoordinatesInOld =
        versionlessCoordinatesToVersionOld.keySet().stream()
            .filter(LibrariesBomReleaseNote::clientLibraryFilter)
            .collect(ImmutableSet.toImmutableSet());

    SetView<String> artifactsOnlyInNew =
        Sets.difference(
            cloudLibrariesVersionlessCoordinatesInNew, cloudLibrariesVersionlessCoordinatesInOld);

    String oldBomVersion = Splitter.on(':').splitToList(oldBom.getCoordinates()).get(3);
    report.append("Here are the differences from the previous version (")
        .append(oldBomVersion)
        .append(")\n\n");

    if (!artifactsOnlyInNew.isEmpty()) {
      report.append("# New Addition\n");
      for (String versionlessCoordinates : artifactsOnlyInNew) {
        report.append(
            "- "
                + versionlessCoordinates
                + ":"
                + versionlessCoordinatesToVersionNew.get(versionlessCoordinates)
                + "\n");
      }
    }

    report.append("# Version Upgrades\n\n");
    report.append("The group ID of the following artifacts is `com.google.cloud`.\n");
    SetView<String> artifactsInBothBoms =
        Sets.intersection(
            cloudLibrariesVersionlessCoordinatesInNew, cloudLibrariesVersionlessCoordinatesInOld);

    List<String> majorVersionBumpVersionlessCoordinates = new ArrayList<>();
    List<String> minorVersionBumpVersionlessCoordinates = new ArrayList<>();
    List<String> patchVersionBumpVersionlessCoordinates = new ArrayList<>();
    for (String versionlessCoordinates : artifactsInBothBoms) {
      String previousVersion = versionlessCoordinatesToVersionOld.get(versionlessCoordinates);
      String currentVersion = versionlessCoordinatesToVersionNew.get(versionlessCoordinates);

      if (isMajorVersionBump(previousVersion, currentVersion)) {
        majorVersionBumpVersionlessCoordinates.add(versionlessCoordinates);
      } else if (isMinorVersionBump(previousVersion, currentVersion)) {
        minorVersionBumpVersionlessCoordinates.add(versionlessCoordinates);
      } else if (isPatchVersionBump(previousVersion, currentVersion)) {
        patchVersionBumpVersionlessCoordinates.add(versionlessCoordinates);
      }
    }
    if (!majorVersionBumpVersionlessCoordinates.isEmpty()) {
      report.append("## Major Version Upgrades\n");
      printClientLibraryVersionDifference(
          majorVersionBumpVersionlessCoordinates,
          versionlessCoordinatesToVersionOld,
          versionlessCoordinatesToVersionNew);
    }

    if (!minorVersionBumpVersionlessCoordinates.isEmpty()) {
      report.append("## Minor Version Upgrades\n");
      printClientLibraryVersionDifference(
          minorVersionBumpVersionlessCoordinates,
          versionlessCoordinatesToVersionOld,
          versionlessCoordinatesToVersionNew);
    }

    if (!patchVersionBumpVersionlessCoordinates.isEmpty()) {
      report.append("## Patch Version Upgrades\n");
      printClientLibraryVersionDifference(
          patchVersionBumpVersionlessCoordinates,
          versionlessCoordinatesToVersionOld,
          versionlessCoordinatesToVersionNew);
    }

    SetView<String> artifactsOnlyInOld =
        Sets.difference(
            cloudLibrariesVersionlessCoordinatesInOld, cloudLibrariesVersionlessCoordinatesInNew);

    if (!artifactsOnlyInOld.isEmpty()) {
      report.append("# Removed artifacts\n");
      for (String versionlessCoordinates : artifactsOnlyInOld) {
        report.append(
            "- "
                + versionlessCoordinates
                + " (prev:"
                + versionlessCoordinatesToVersionOld.get(versionlessCoordinates)
                + ")\n");
      }
    }
  }

  private static void printClientLibraryVersionDifference(
      Iterable<String> artifactsInBothBoms,
      Map<String, String> versionlessCoordinatesToVersionOld,
      Map<String, String> versionlessCoordinatesToVersionNew)
      throws MavenRepositoryException {
    for (String versionlessCoordinates : artifactsInBothBoms) {
      StringBuilder line = new StringBuilder("- ");

      String previousVersion = versionlessCoordinatesToVersionOld.get(versionlessCoordinates);
      String currentVersion = versionlessCoordinatesToVersionNew.get(versionlessCoordinates);

      List<String> groupIdAndArtifactId = Splitter.on(":").splitToList(versionlessCoordinates);
      Verify.verify(
          groupIdAndArtifactId.size() == 2,
          "Versionless coordinates should have 2 elements separated by ':'");
      String groupId = groupIdAndArtifactId.get(0);
      String artifactId = groupIdAndArtifactId.get(1);
      line.append(
          ("com.google.cloud".equals(groupId) ? artifactId : versionlessCoordinates)
              + ":"
              + currentVersion
              + " (prev:"
              + previousVersion
              + "; Release Notes: ");

      ImmutableList<String> versionsForReleaseNotes =
          clientLibraryReleaseNoteVersions(versionlessCoordinates, previousVersion, currentVersion);

      String libraryName = null;
      if (artifactId.contains("google-cloud-")) {
        libraryName = artifactId.replace("google-cloud-", "");
      } else if (artifactId.contains("google-")) {
        // Case of google-iam-admin
        libraryName = artifactId.replace("google-", "");
      }

      String repositoryUrl = "https://github.com/googleapis/java-" + libraryName;
      List<String> links = new ArrayList<>();
      for (String versionForReleaseNotes : versionsForReleaseNotes) {
        String[] versionAndQualifier = versionForReleaseNotes.split("-");
        String version = versionAndQualifier[0];
        String releaseUrl = repositoryUrl + "/releases/tag/v" + version;
        links.add(String.format("[v%s](%s)", versionForReleaseNotes, releaseUrl));
      }
      line.append(Joiner.on(", ").join(links)).append(")");

      report.append(line).append("\n");
    }
  }

  /**
   * Returns the versions to link release notes of individual client libraries. The list includes
   * the {@code currentVersion} but not including {@code previousVersion}.
   */
  private static ImmutableList<String> clientLibraryReleaseNoteVersions(
      String versionlessCoordinates, String previousVersion, String currentVersion)
      throws MavenRepositoryException {
    DefaultArtifact artifact = new DefaultArtifact(versionlessCoordinates + ":" + currentVersion);

    // The highest versions come last.
    ImmutableList<String> versions =
        RepositoryUtility.findVersions(
            repositorySystem, artifact.getGroupId(), artifact.getArtifactId());

    Verify.verify(
        !versions.isEmpty(),
        "The versions returned empty. "
            + "Please check the coordinates (groupId: %s, artifactId: %s)",
        artifact.getGroupId(),
        artifact.getArtifactId());

    ImmutableList.Builder<String> releaseNoteVersions = ImmutableList.builder();

    for (String version : versions) {
      if (version.contains("-SNAPSHOT")) {
        continue;
      }
      // The compare method returns negative if the first argument is less than the second.
      if (versionComparator.compare(previousVersion, version) < 0
          && versionComparator.compare(version, currentVersion) <= 0) {
        releaseNoteVersions.add(version);
      }
    }
    return releaseNoteVersions.build();
  }

  private static boolean isMajorVersionBump(String previousVersion, String currentVersion) {
    List<String> previousVersionElements = dotSplitter.splitToList(previousVersion);
    List<String> currentVersionElements = dotSplitter.splitToList(currentVersion);
    return !previousVersionElements.get(0).equals(currentVersionElements.get(0));
  }

  private static boolean isMinorVersionBump(String previousVersion, String currentVersion) {
    List<String> previousVersionElements = dotSplitter.splitToList(previousVersion);
    List<String> currentVersionElements = dotSplitter.splitToList(currentVersion);
    return previousVersionElements.get(0).equals(currentVersionElements.get(0))
        && !previousVersionElements.get(1).equals(currentVersionElements.get(1));
  }
  private static boolean isPatchVersionBump(String previousVersion, String currentVersion) {
    List<String> previousVersionElements = dotSplitter.splitToList(previousVersion);
    List<String> currentVersionElements = dotSplitter.splitToList(currentVersion);
    return previousVersionElements.get(0).equals(currentVersionElements.get(0))
        && previousVersionElements.get(1).equals(currentVersionElements.get(1))
        && !previousVersionElements.get(2).equals(currentVersionElements.get(2));
  }
}
