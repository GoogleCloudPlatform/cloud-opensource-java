package com.google.cloud.tools.opensource.dependencies;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

/** Tool to generate the changes in the content of the BOM since the previous release. */
class BomReleaseNote {

  private static final RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();
  private static final VersionComparator versionComparator = new VersionComparator();
  private static final Splitter dotSplitter = Splitter.on(".");
  private static final String cloudLibraryArtifactPrefix = "com.google.cloud:google-cloud-";

  public static void main(String[] arguments)
      throws ArtifactDescriptorException, MavenRepositoryException {
    if (arguments.length != 1) {
      System.out.println(
          "Please provide BOM coordinates. For example, com.google.cloud:libraries-bom:25.0.0");
      System.exit(1);
    }
    String bomCoordinates = arguments[0];
    Bom bom = Bom.readBom(bomCoordinates);

    Bom previousBom = previousBom(bom);
    printCloudClientBomDifference(previousBom, bom);
  }

  /**
   * Returns the BOM that was released prior to the {@code bom}, asking Maven repositories for
   * available versions.
   */
  private static Bom previousBom(Bom bom)
      throws MavenRepositoryException, ArtifactDescriptorException {
    String coordinates = bom.getCoordinates();
    DefaultArtifact bomArtifact = new DefaultArtifact(coordinates);

    // The highest versions come last.
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
    for (Artifact managedDependency : bom.getManagedDependencies()) {
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

    Predicate<String> clientLibraryFilter =
        coordinates ->
            coordinates.contains(cloudLibraryArtifactPrefix)
                && !coordinates.contains("google-cloud-core");
    ImmutableSet<String> cloudLibrariesVersionlessCoordinatesInNew =
        versionlessCoordinatesToVersionNew.keySet().stream()
            .filter(clientLibraryFilter)
            .collect(ImmutableSet.toImmutableSet());
    ImmutableSet<String> cloudLibrariesVersionlessCoordinatesInOld =
        versionlessCoordinatesToVersionOld.keySet().stream()
            .filter(clientLibraryFilter)
            .collect(ImmutableSet.toImmutableSet());

    SetView<String> artifactsOnlyInNew =
        Sets.difference(
            cloudLibrariesVersionlessCoordinatesInNew, cloudLibrariesVersionlessCoordinatesInOld);

    if (!artifactsOnlyInNew.isEmpty()) {
      System.out.println("# New addition:");
      for (String versionlessCoordinates : artifactsOnlyInNew) {
        System.out.println(
            "- "
                + versionlessCoordinates
                + ":"
                + versionlessCoordinatesToVersionNew.get(versionlessCoordinates));
      }
    }

    System.out.println("# Version Upgrades");
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
      } else {
        patchVersionBumpVersionlessCoordinates.add(versionlessCoordinates);
      }
    }
    System.out.println("## Major Version Upgrades");
    printClientLibraryVersionDifference(
        majorVersionBumpVersionlessCoordinates,
        versionlessCoordinatesToVersionOld,
        versionlessCoordinatesToVersionNew);

    System.out.println("## Minor Version Upgrades");
    printClientLibraryVersionDifference(
        minorVersionBumpVersionlessCoordinates,
        versionlessCoordinatesToVersionOld,
        versionlessCoordinatesToVersionNew);

    System.out.println("## Patch Version Upgrades");
    printClientLibraryVersionDifference(
        patchVersionBumpVersionlessCoordinates,
        versionlessCoordinatesToVersionOld,
        versionlessCoordinatesToVersionNew);

    SetView<String> artifactsOnlyInOld =
        Sets.difference(
            cloudLibrariesVersionlessCoordinatesInOld, cloudLibrariesVersionlessCoordinatesInNew);

    if (!artifactsOnlyInOld.isEmpty()) {
      System.out.println("# Removed artifacts");
      for (String versionlessCoordinates : artifactsOnlyInOld) {
        System.out.println(
            "- "
                + versionlessCoordinates
                + " (prev:"
                + versionlessCoordinatesToVersionOld.get(versionlessCoordinates)
                + ")");
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
      line.append(
          versionlessCoordinates
              + ":"
              + currentVersion
              + " (prev:"
              + previousVersion
              + "; Release Notes: ");

      ImmutableList<String> versionsForReleaseNotes =
          clientLibraryReleaseNoteVersions(versionlessCoordinates, previousVersion, currentVersion);

      String artifactSuffix = versionlessCoordinates.replace(cloudLibraryArtifactPrefix, "");
      String repositoryUrl = "https://github.com/googleapis/java-" + artifactSuffix;
      List<String> links = new ArrayList<>();
      for (String versionForReleaseNotes : versionsForReleaseNotes) {
        String[] versionAndQualifier = versionForReleaseNotes.split("-");
        String version = versionAndQualifier[0];
        String releaseUrl = repositoryUrl + "/releases/tag/v" + version;
        links.add(String.format("[v%s](%s)", versionForReleaseNotes, releaseUrl));
      }
      line.append(Joiner.on(", ").join(links)).append(")");

      System.out.println(line);
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
    Splitter dotSplitter = Splitter.on(".");
    List<String> previousVersionElements = dotSplitter.splitToList(previousVersion);
    List<String> currentVersionElements = dotSplitter.splitToList(currentVersion);
    return previousVersionElements.get(0).equals(currentVersionElements.get(0))
        && !previousVersionElements.get(1).equals(currentVersionElements.get(1));
  }
}
