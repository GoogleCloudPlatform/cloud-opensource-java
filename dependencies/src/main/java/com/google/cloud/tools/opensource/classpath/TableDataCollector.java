package com.google.cloud.tools.opensource.classpath;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedListMultimap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

public class TableDataCollector {

  private static final Logger logger = Logger.getLogger(TableDataCollector.class.getName());

  SymbolProblemSerializer serializer = new SymbolProblemSerializer();

  AtomicLong totalFinishedCheckCount = new AtomicLong(0);
  AtomicLong totalFailedCheckCount = new AtomicLong(0);

  private static String bomCoordinates = null;

  // com.google.api:gax-grpc:[1.38.0,]
  // com.google.api:gax:[1.38.0,]
  public static void main(String[] arguments) throws Exception {
    TableDataCollector tableDataCollector = new TableDataCollector();

    if (arguments[0].contains("libraries-bom")) {
      bomCoordinates = arguments[0];
    }
    if (bomCoordinates != null) {
      Bom bom = RepositoryUtility.readBom(bomCoordinates);
      ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();
      DependencyGraphBuilder.configureManagedDependencies(managedDependencies);
      ImmutableList<String> coordinates = managedDependencies.stream().map(Artifacts::toCoordinates).collect(toImmutableList());
      tableDataCollector.runTableCrossCheck(coordinates);
    } else {
      ImmutableList<String> coordinates = generateCoordinatesFromArguments(arguments);
      tableDataCollector.runTableCrossCheck(coordinates);
    }
  }


  static ImmutableList<String> generateCoordinatesFromArguments(String[] arguments) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (String argument : arguments) {
      String[] split = argument.split(":");
      if (split.length != 3) {
        throw new IllegalArgumentException(
            "Element of arguments must be <groupId>:<artifactId>:[X,]");
      }
      String groupId = split[0];
      String artifactId = split[1];
      String versionRange = split[2];
      ImmutableList<String> coordinates = artifactCoordinatesFromVersionRange(groupId, artifactId,
          versionRange);
      builder.addAll(coordinates);
    }
    return builder.build();
  }

  static ImmutableList<String> artifactCoordinatesFromVersionRange(String groupId,
      String artifactId, String versionRange) {
    RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility
        .newSession(repositorySystem);

    Artifact artifactWithVersionRange = new DefaultArtifact(groupId, artifactId, null,
        versionRange);
    VersionRangeRequest request =
        new VersionRangeRequest(
            artifactWithVersionRange, ImmutableList.of(RepositoryUtility.CENTRAL), null);

    try {
      VersionRangeResult versionRangeResult = repositorySystem
          .resolveVersionRange(session, request);
      return versionRangeResult.getVersions().stream()
          .filter(version -> !version.toString().toLowerCase().contains("rc"))
          .map(x -> String.format("%s:%s:%s", groupId, artifactId, x))
          .collect(toImmutableList());
    } catch (VersionRangeResolutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void runTableCrossCheck(List<String> coordinates)
      throws InterruptedException {
    runTableCheck(coordinates, coordinates);
  }

  private void runTableCheck(List<String> rowKeys, List<String> columnKeys)
      throws InterruptedException {
    int totalCellCount = rowKeys.size() * columnKeys.size();

    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(18);

    for (String rowKey : rowKeys) {
      for (String columnKey : columnKeys) {

        executor.submit(() -> {
          try {
            checkPair(rowKey, columnKey);
            logger.info(
                String.format("Finished %s x %s (%d/%d)", rowKey, columnKey, totalFinishedCheckCount.incrementAndGet(), totalCellCount));
          } catch (Exception ex) {
            logger.warning(
                String.format("Failed to process %s x %s: %s", rowKey, columnKey, ex.getMessage()));
            totalFailedCheckCount.incrementAndGet();
            throw new RuntimeException("Could not process " + rowKey + " x " + columnKey, ex);
          }
        });
      }
    }

    executor.shutdown();
    if (!executor.awaitTermination(100, TimeUnit.HOURS)) {
      logger.severe("Waited but not finished in the time limit");
      executor.shutdownNow();
      if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
        logger.severe("Waited another second but not finished in the time limit");
      }
    }
    logger.info(
        "Finished " + totalFinishedCheckCount.get() + " tasks. Failures: " + totalFailedCheckCount
            .get() + " tasks");
    logger.info("Row keys: "+rowKeys);
  }

  private void checkPair(String coordinates1, String coordinates2)
      throws IOException {

    Path output = pairFilePath(coordinates1, coordinates2, bomCoordinates);

    if (output.toFile().exists()) {

      try {
        serializer.validate(output);
        logger.fine("Already exists: " + output);
        return;
      } catch (Exception ex) {
        logger.warning("ex: "+ ex.getMessage());
      }
    }

    if (areSameArtifactInDifferentVersion(coordinates1, coordinates2)) {
      return;
    }

    runLinkageCheckOnPair(coordinates1, coordinates2, output);
  }

  static boolean areSameArtifactInDifferentVersion(String coordinates1,
      String coordinates2) {
    Artifact artifact1 = new DefaultArtifact(coordinates1);
    Artifact artifact2 = new DefaultArtifact(coordinates2);

    if (artifact1.getVersion().equals(artifact2.getVersion())) {
      return false;
    }
    if (artifact1.getArtifactId().equals(artifact2.getArtifactId())
        && artifact2.getGroupId().equals(artifact2.getGroupId())) {
      return true;
    }
    return false;
  }


  private void runLinkageCheckOnPair(String coordinates1, String coordinates2, Path output)
      throws IOException {

    List<Artifact> artifacts = ImmutableList.of(
        new DefaultArtifact(coordinates1),
        new DefaultArtifact(coordinates2));

    LinkedListMultimap<Path, DependencyPath> jarToDependencyPaths;
    try {
      jarToDependencyPaths =
          ClassPathBuilder.artifactsToDependencyPaths(artifacts, true);
    } catch (RepositoryException ex) {
      serializer.serialize(ex, output);
      return;
    }
    ImmutableList<Path> classpath = ImmutableList.copyOf(jarToDependencyPaths.keySet());
    List<Path> entryPointJars = classpath.subList(0, Math.min(artifacts.size(), classpath.size()));

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        LinkageChecker.create(classpath, ImmutableSet.copyOf(entryPointJars)).findSymbolProblems();

    serializer.serialize(symbolProblems, jarToDependencyPaths, output);
  }

  public static Path pairFilePath(String coordinates1, String coordinates2, String bomCoordinates) {
    if (bomCoordinates != null) {
      String bomPath = bomCoordinates.replace(':', '_');
      Path bomDirectory = Paths.get("linkage-check-cache").resolve(bomPath);
      bomDirectory.toFile().mkdirs();
      return bomDirectory.resolve(pairFileName(coordinates1, coordinates2));
    } else {
      return Paths.get("linkage-check-cache").resolve(pairFileName(coordinates1, coordinates2));
    }
  }

  public static Path pairFileName(String coordinates1, String coordinates2) {
    return Paths
        .get(coordinates1.replace(':', '_') + "___" + coordinates2.replace(':', '_') + ".json");
  }

}
