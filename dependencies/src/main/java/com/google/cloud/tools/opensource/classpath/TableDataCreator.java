package com.google.cloud.tools.opensource.classpath;

import static com.google.cloud.tools.opensource.classpath.TableDataCollector.areSameArtifactInDifferentVersion;
import static com.google.cloud.tools.opensource.classpath.TableDataCollector.pairFileName;
import static com.google.cloud.tools.opensource.classpath.TableDataCollector.pairFilePath;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.cloud.tools.opensource.serializable.LinkageCheckResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class TableDataCreator {

  private static final Logger logger = Logger.getLogger(TableDataCreator.class.getName());

  SymbolProblemSerializer serializer = new SymbolProblemSerializer();

  AtomicLong totalFinishedCheckCount = new AtomicLong(0);
  AtomicLong totalFailedCheckCount = new AtomicLong(0);

  private static String bomCoordinates = null;
  final private List<String> coordinates;


  // com.google.api:gax-grpc:[1.38.0,]
  // com.google.api:gax:[1.38.0,]
  public static void main(String[] arguments) throws Exception {
    bomCoordinates = arguments[0];
    Bom bom = RepositoryUtility.readBom(bomCoordinates);
    ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();
    DependencyGraphBuilder.configureManagedDependencies(managedDependencies);
    ImmutableList<String> coordinates = managedDependencies.stream().map(Artifacts::toCoordinates)
        .collect(toImmutableList());
    TableDataCreator creator = new TableDataCreator(coordinates);
    creator.generateTable(coordinates);
  }

  private TableDataCreator(List<String> coordinates) {
    this.coordinates = coordinates;
  }

  private void generateTable(List<String> coordinates)
      throws InterruptedException, IOException {

    ImmutableMultimap.Builder<String, com.google.cloud.tools.opensource.serializable.SymbolProblem> builder = ImmutableMultimap
        .builder();
    for (String c : coordinates) {
      builder.putAll(c, loadSymbolProblem(c, c));
    }

    ImmutableMap<String, Object> data = generateTable(coordinates, coordinates,
        builder.build());

    String bomPath = bomCoordinates.replace(':', '_');
    Path tableJsonFile = Paths.get("linkage-check-cache").resolve(bomPath).resolve("table.json");
    serializer.serialize(data, tableJsonFile);
  }

  private ImmutableMap<String, Object> generateTable(List<String> rowKeys, List<String> columnKeys,
      ImmutableMultimap<String, com.google.cloud.tools.opensource.serializable.SymbolProblem> inherentProblems)
      throws InterruptedException {

    int count = 0;
    int totalCellCount = rowKeys.size() * columnKeys.size();
    ImmutableMap.Builder<String, Object> tableData = ImmutableMap.builder();
    for (String rowKey : rowKeys) {
      for (String columnKey : columnKeys) {
        ImmutableSet.Builder<com.google.cloud.tools.opensource.serializable.SymbolProblem> inherentProblemBuilder = ImmutableSet
            .builder();
        inherentProblemBuilder.addAll(inherentProblems.get(rowKey));
        inherentProblemBuilder.addAll(inherentProblems.get(columnKey));

        try {
          checkPair(rowKey, columnKey, inherentProblemBuilder.build(), tableData);
          logger.info("Finished " + (count++) + " / " + totalCellCount);
        } catch (Exception ex) {
          logger.warning(
              String.format("Failed to process %s x %s: %s", rowKey, columnKey, ex.getMessage()));
          totalFailedCheckCount.incrementAndGet();
          throw new RuntimeException("Could not process " + rowKey + " x " + columnKey, ex);
        }
      }
    }

    return tableData.build();
  }

  private ImmutableSet<com.google.cloud.tools.opensource.serializable.SymbolProblem> loadSymbolProblem(
      String coordinates1, String coordinates2) {
    Path input = pairFilePath(coordinates1, coordinates2, bomCoordinates);

    if (input.toFile().exists()) {
      try {
        LinkageCheckResult result = serializer.deserialize(input);

        ImmutableSet<com.google.cloud.tools.opensource.serializable.SymbolProblem> symbolProblems = ImmutableSet
            .copyOf(result.getSymbolProblems().values());
        return symbolProblems;
      } catch (Exception ex) {
        logger.warning("ex: " + ex.getMessage());
      }
    }
    logger.warning("The file does not exist: " + input);
    return ImmutableSet.of();
  }

  /**
   * Outputs table cell tag to {@code tableBuilder}.
   */
  private void checkPair(String coordinates1, String coordinates2,
      ImmutableSet<com.google.cloud.tools.opensource.serializable.SymbolProblem> inherentProblems,
      ImmutableMap.Builder<String, Object> tableData)
      throws IOException {

    String key = coordinates1 + "___" + coordinates2;
    if (areSameArtifactInDifferentVersion(coordinates1, coordinates2)) {
      tableData.put(key, inherentProblems.size());
      return;
    }

    try {
      ImmutableSet<com.google.cloud.tools.opensource.serializable.SymbolProblem> symbolProblemsByCombination = loadSymbolProblem(
          coordinates1, coordinates2);
      SetView<com.google.cloud.tools.opensource.serializable.SymbolProblem> problemsOnlyInCombination = Sets
          .difference(symbolProblemsByCombination, inherentProblems);

      int size = problemsOnlyInCombination.size();
      tableData.put(key, size);
    } catch (Exception ex) {
      tableData.put(key, "error");
    }
  }
}
