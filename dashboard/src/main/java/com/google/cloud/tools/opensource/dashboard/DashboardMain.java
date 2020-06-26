/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.opensource.dashboard;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.DependencyTreeFormatter;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.cloud.tools.opensource.dependencies.Update;
import com.google.cloud.tools.opensource.dependencies.VersionComparator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateHashModel;
import freemarker.template.Version;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

public class DashboardMain {

  public static final String TEST_NAME_LINKAGE_CHECK = "Linkage Errors";
  public static final String TEST_NAME_UPPER_BOUND = "Upper Bounds";
  public static final String TEST_NAME_GLOBAL_UPPER_BOUND = "Global Upper Bounds";
  public static final String TEST_NAME_DEPENDENCY_CONVERGENCE = "Dependency Convergence";

  private static final Configuration freemarkerConfiguration = configureFreemarker();

  private static final DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder();
  private static final ClassPathBuilder classPathBuilder =
      new ClassPathBuilder(dependencyGraphBuilder);

  /**
   * Generates a code hygiene dashboard for a BOM. This tool takes a path to pom.xml of the BOM as
   * an argument or Maven coordinates to a BOM.
   *
   * <p>Generated dashboard is at {@code target/$groupId/$artifactId/$version/index.html}, where
   * each value is from BOM coordinates except {@code $version} is "snapshot" if the BOM has
   * snapshot version.
   */
  public static void main(String[] arguments)
      throws IOException, TemplateException, RepositoryException, URISyntaxException,
      ParseException, MavenRepositoryException {
    DashboardArguments dashboardArguments = DashboardArguments.readCommandLine(arguments);

    if (dashboardArguments.hasVersionlessCoordinates()) {
      generateAllVersions(dashboardArguments.getVersionlessCoordinates());
    } else if (dashboardArguments.hasFile()) {
      generate(dashboardArguments.getBomFile());
    } else {
      generate(dashboardArguments.getBomCoordinates());
    }
  }

  private static void generateAllVersions(String versionlessCoordinates)
      throws IOException, TemplateException, RepositoryException, URISyntaxException,
      MavenRepositoryException {
    List<String> elements = Splitter.on(':').splitToList(versionlessCoordinates);
    checkArgument(
        elements.size() == 2,
        "The versionless coordinates should have one colon character: " + versionlessCoordinates);
    String groupId = elements.get(0);
    String artifactId = elements.get(1);

    RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();
    ImmutableList<String> versions =
        RepositoryUtility.findVersions(repositorySystem, groupId, artifactId);
    for (String version : versions) {
      generate(String.format("%s:%s:%s", groupId, artifactId, version));
    }
    generateVersionIndex(groupId, artifactId, versions);
  }

  @VisibleForTesting
  static Path generateVersionIndex(String groupId, String artifactId, List<String> versions)
      throws IOException, TemplateException, URISyntaxException {
    Path directory = outputDirectory(groupId, artifactId, "snapshot").getParent();
    directory.toFile().mkdirs();
    Path page = directory.resolve("index.html");

    Map<String, Object> templateData = new HashMap<>();
    templateData.put("versions", versions);
    templateData.put("groupId", groupId);
    templateData.put("artifactId", artifactId);

    File dashboardFile = page.toFile();
    try (Writer out =
        new OutputStreamWriter(new FileOutputStream(dashboardFile), StandardCharsets.UTF_8)) {
      Template dashboard = freemarkerConfiguration.getTemplate("/templates/version_index.ftl");
      dashboard.process(templateData, out);
    }

    copyResource(directory, "css/dashboard.css");

    return page;
  }

  @VisibleForTesting
  static Path generate(String bomCoordinates)
      throws IOException, TemplateException, RepositoryException, URISyntaxException {
    Path output = generate(Bom.readBom(bomCoordinates));
    System.out.println("Wrote dashboard for " + bomCoordinates + " to " + output);
    return output;
  }

  @VisibleForTesting
  static Path generate(Path bomFile)
      throws IOException, TemplateException, URISyntaxException, MavenRepositoryException {
    checkArgument(Files.isRegularFile(bomFile), "The input BOM %s is not a regular file", bomFile);
    checkArgument(Files.isReadable(bomFile), "The input BOM %s is not readable", bomFile);
    Path output = generate(Bom.readBom(bomFile));
    System.out.println("Wrote dashboard for " + bomFile + " to " + output);
    return output;
  }

  private static Path generate(Bom bom) throws IOException, TemplateException, URISyntaxException {

    ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();

    ClassPathResult classPathResult = classPathBuilder.resolve(managedDependencies);
    ImmutableList<ClassPathEntry> classpath = classPathResult.getClassPath();

    LinkageChecker linkageChecker = LinkageChecker.create(classpath);

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.findSymbolProblems();

    ArtifactCache cache = loadArtifactInfo(managedDependencies);
    Path output = generateHtml(bom, cache, classPathResult, symbolProblems);

    return output;
  }

  private static Path outputDirectory(String groupId, String artifactId, String version) {
    String versionPathElement = version.contains("-SNAPSHOT") ? "snapshot" : version;
    return Paths.get("target", groupId, artifactId, versionPathElement);
  }

  private static Path generateHtml(
      Bom bom,
      ArtifactCache cache,
      ClassPathResult classPathResult,
      ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems)
      throws IOException, TemplateException, URISyntaxException {

    Artifact bomArtifact = new DefaultArtifact(bom.getCoordinates());

    Path relativePath =
        outputDirectory(
            bomArtifact.getGroupId(), bomArtifact.getArtifactId(), bomArtifact.getVersion());
    Path output = Files.createDirectories(relativePath);

    copyResource(output, "css/dashboard.css");
    copyResource(output, "js/dashboard.js");

    ImmutableMap<ClassPathEntry, ImmutableSetMultimap<SymbolProblem, String>> symbolProblemTable =
        indexByJar(symbolProblems);

    List<ArtifactResults> table =
        generateReports(
            freemarkerConfiguration, output, cache, symbolProblemTable, classPathResult, bom);

    generateDashboard(
        freemarkerConfiguration,
        output,
        table,
        cache.getGlobalDependencies(),
        symbolProblemTable,
        classPathResult,
        bom);

    return output;
  }

  private static void copyResource(Path output, String resourceName)
      throws IOException, URISyntaxException {
    ClassLoader classLoader = DashboardMain.class.getClassLoader();
    Path input = Paths.get(classLoader.getResource(resourceName).toURI()).toAbsolutePath();
    Path copy = output.resolve(input.getFileName());
    if (!Files.exists(copy)) {
      Files.copy(input, copy);
    }
  }

  @VisibleForTesting
  static Configuration configureFreemarker() {
    Configuration configuration = new Configuration(new Version("2.3.28"));
    configuration.setDefaultEncoding("UTF-8");
    configuration.setClassForTemplateLoading(DashboardMain.class, "/");
    return configuration;
  }

  @VisibleForTesting
  static List<ArtifactResults> generateReports(
      Configuration configuration,
      Path output,
      ArtifactCache cache,
      ImmutableMap<ClassPathEntry, ImmutableSetMultimap<SymbolProblem, String>> symbolProblemTable,
      ClassPathResult classPathResult,
      Bom bom) throws TemplateException {

    Map<Artifact, ArtifactInfo> artifacts = cache.getInfoMap();
    List<ArtifactResults> table = new ArrayList<>();
    for (Entry<Artifact, ArtifactInfo> entry : artifacts.entrySet()) {
      ArtifactInfo info = entry.getValue();
      try {
        if (info.getException() != null) {
          ArtifactResults unavailable = new ArtifactResults(entry.getKey());
          unavailable.setExceptionMessage(info.getException().getMessage());
          table.add(unavailable);
        } else {
          Artifact artifact = entry.getKey();
          ImmutableSet<ClassPathEntry> jarsInDependencyTree =
              classPathResult.getClassPathEntries(Artifacts.toCoordinates(artifact));
          Map<ClassPathEntry, ImmutableSetMultimap<SymbolProblem, String>>
              relevantSymbolProblemTable =
                  Maps.filterKeys(symbolProblemTable, jarsInDependencyTree::contains);

          ArtifactResults results =
              generateArtifactReport(
                  configuration,
                  output,
                  artifact,
                  entry.getValue(),
                  cache.getGlobalDependencies(),
                  ImmutableMap.copyOf(relevantSymbolProblemTable),
                  classPathResult,
                  bom);
          table.add(results);
        }
      } catch (IOException ex) {
        ArtifactResults unavailableTestResult = new ArtifactResults(entry.getKey());
        unavailableTestResult.setExceptionMessage(ex.getMessage());
        // Even when there's a problem generating test result, show the error in the dashboard
        table.add(unavailableTestResult);
      }
    }

    return table;
  }

  /**
   * This is the only method that queries the Maven repository.
   */
  private static ArtifactCache loadArtifactInfo(List<Artifact> artifacts) {
    Map<Artifact, ArtifactInfo> infoMap = new LinkedHashMap<>();
    List<DependencyGraph> globalDependencies = new ArrayList<>();

    for (Artifact artifact : artifacts) {
      DependencyGraph completeDependencies =
          dependencyGraphBuilder.buildFullDependencyGraph(ImmutableList.of(artifact));
      globalDependencies.add(completeDependencies);

      // picks versions according to Maven rules
      DependencyGraph transitiveDependencies =
          dependencyGraphBuilder.buildMavenDependencyGraph(new Dependency(artifact, "compile"));

      ArtifactInfo info = new ArtifactInfo(completeDependencies, transitiveDependencies);
      infoMap.put(artifact, info);
    }

    ArtifactCache cache = new ArtifactCache();
    cache.setInfoMap(infoMap);
    cache.setGlobalDependencies(globalDependencies);

    return cache;
  }

  private static ArtifactResults generateArtifactReport(
      Configuration configuration,
      Path output,
      Artifact artifact,
      ArtifactInfo artifactInfo,
      List<DependencyGraph> globalDependencies,
      ImmutableMap<ClassPathEntry, ImmutableSetMultimap<SymbolProblem, String>> symbolProblemTable,
      ClassPathResult classPathResult,
      Bom bom)
      throws IOException, TemplateException {

    String coordinates = Artifacts.toCoordinates(artifact);
    File outputFile = output.resolve(coordinates.replace(':', '_') + ".html").toFile();

    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {

      // includes all versions
      DependencyGraph completeDependencies = artifactInfo.getCompleteDependencies();
      List<Update> convergenceIssues = completeDependencies.findUpdates();

      // picks versions according to Maven rules
      DependencyGraph transitiveDependencies = artifactInfo.getTransitiveDependencies();

      Map<Artifact, Artifact> upperBoundFailures =
          findUpperBoundsFailures(completeDependencies.getHighestVersionMap(),
              transitiveDependencies);

      Map<Artifact, Artifact> globalUpperBoundFailures = findUpperBoundsFailures(
          collectLatestVersions(globalDependencies), transitiveDependencies);

      List<DependencyPath> dependencyPaths = completeDependencies.list();

      long totalLinkageErrorCount =
          symbolProblemTable.values().stream()
              .flatMap(problemToClasses -> problemToClasses.keySet().stream())
              .distinct()
              .count();

      ListMultimap<DependencyPath, DependencyPath> dependencyTree =
          DependencyTreeFormatter.buildDependencyPathTree(dependencyPaths);
      Template report = configuration.getTemplate("/templates/component.ftl");

      DependencyPath rootNode = Iterables.getFirst(dependencyTree.values(), null);
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("artifact", artifact);
      templateData.put("updates", convergenceIssues);
      templateData.put("upperBoundFailures", upperBoundFailures);
      templateData.put("globalUpperBoundFailures", globalUpperBoundFailures);
      templateData.put("lastUpdated", LocalDateTime.now());
      templateData.put("dependencyTree", dependencyTree);
      templateData.put("dependencyRootNode", rootNode);
      templateData.put("symbolProblems", symbolProblemTable);
      templateData.put("classPathResult", classPathResult);
      templateData.put("totalLinkageErrorCount", totalLinkageErrorCount);
      templateData.put("coordinates", bom.getCoordinates());
      report.process(templateData, out);

      ArtifactResults results = new ArtifactResults(artifact);
      results.addResult(TEST_NAME_UPPER_BOUND, upperBoundFailures.size());
      results.addResult(TEST_NAME_GLOBAL_UPPER_BOUND, globalUpperBoundFailures.size());
      results.addResult(TEST_NAME_DEPENDENCY_CONVERGENCE, convergenceIssues.size());
      results.addResult(TEST_NAME_LINKAGE_CHECK, (int) totalLinkageErrorCount);
      results.setDependencyTree(dependencyTree);

      return results;
    }
  }

  private static Map<Artifact, Artifact> findUpperBoundsFailures(
      Map<String, String> expectedVersionMap,
      DependencyGraph transitiveDependencies) {

    Map<String, String> actualVersionMap = transitiveDependencies.getHighestVersionMap();

    VersionComparator comparator = new VersionComparator();

    Map<Artifact, Artifact> upperBoundFailures = new LinkedHashMap<>();

    for (String id : expectedVersionMap.keySet()) {
      String expectedVersion = expectedVersionMap.get(id);
      String actualVersion = actualVersionMap.get(id);
      // Check that the actual version is not null because it is
      // possible for dependencies to appear or disappear from the tree
      // depending on which version of another dependency is loaded.
      // In both cases, no action is needed.
      if (actualVersion != null && comparator.compare(actualVersion, expectedVersion) < 0) {
        // Maven did not choose highest version
        DefaultArtifact lower = new DefaultArtifact(id + ":" + actualVersion);
        DefaultArtifact upper = new DefaultArtifact(id + ":" + expectedVersion);
        upperBoundFailures.put(lower, upper);
      }
    }
    return upperBoundFailures;
  }

  /**
   * Partitions {@code symbolProblems} by the JAR file that contains the {@link ClassFile}.
   *
   * <p>For example, {@code classes = result.get(JarX).get(SymbolProblemY)} where {@code classes}
   * are not null means that {@code JarX} has {@code SymbolProblemY} and that {@code JarX} contains
   * {@code classes} which reference {@code SymbolProblemY.getSymbol()}.
   */
  private static ImmutableMap<ClassPathEntry, ImmutableSetMultimap<SymbolProblem, String>>
      indexByJar(ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems) {

    ImmutableMap<ClassPathEntry, Collection<Entry<SymbolProblem, ClassFile>>> jarMap =
        Multimaps.index(symbolProblems.entries(), entry -> entry.getValue().getClassPathEntry())
            .asMap();

    return ImmutableMap.copyOf(
        Maps.transformValues(
            jarMap,
            entries ->
                ImmutableSetMultimap.copyOf(
                    Multimaps.transformValues(
                        ImmutableSetMultimap.copyOf(entries), ClassFile::getBinaryName))));
  }

  @VisibleForTesting
  static void generateDashboard(
      Configuration configuration,
      Path output,
      List<ArtifactResults> table,
      List<DependencyGraph> globalDependencies,
      ImmutableMap<ClassPathEntry, ImmutableSetMultimap<SymbolProblem, String>> symbolProblemTable,
      ClassPathResult classPathResult,
      Bom bom)
      throws IOException, TemplateException {

    Map<String, String> latestArtifacts = collectLatestVersions(globalDependencies);

    Map<String, Object> templateData = new HashMap<>();
    templateData.put("table", table);
    templateData.put("lastUpdated", LocalDateTime.now());
    templateData.put("latestArtifacts", latestArtifacts);
    templateData.put("symbolProblems", symbolProblemTable);
    templateData.put("classPathResult", classPathResult);
    templateData.put("dependencyPathRootCauses", findRootCauses(classPathResult));
    templateData.put("coordinates", bom.getCoordinates());

    // Accessing static methods from Freemarker template
    // https://freemarker.apache.org/docs/pgui_misc_beanwrapper.html#autoid_60
    DefaultObjectWrapper wrapper = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_28)
        .build();
    TemplateHashModel staticModels = wrapper.getStaticModels();
    templateData.put("dashboardMain", staticModels.get(DashboardMain.class.getName()));
    templateData.put("pieChart", staticModels.get(PieChart.class.getName()));

    File dashboardFile = output.resolve("index.html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(dashboardFile), StandardCharsets.UTF_8)) {
      Template dashboard = configuration.getTemplate("/templates/index.ftl");
      dashboard.process(templateData, out);
    }

    File detailsFile = output.resolve("artifact_details.html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(detailsFile), StandardCharsets.UTF_8)) {
      Template details = configuration.getTemplate("/templates/artifact_details.ftl");
      details.process(templateData, out);
    }

    File unstable = output.resolve("unstable_artifacts.html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(unstable), StandardCharsets.UTF_8)) {
      Template details = configuration.getTemplate("/templates/unstable_artifacts.ftl");
      details.process(templateData, out);
    }

    File dependencyTrees = output.resolve("dependency_trees.html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(dependencyTrees), StandardCharsets.UTF_8)) {
      Template details = configuration.getTemplate("/templates/dependency_trees.ftl");
      details.process(templateData, out);
    }
  }

  private static Map<String, String> collectLatestVersions(
      List<DependencyGraph> globalDependencies) {
    Map<String, String> latestArtifacts = new TreeMap<>();
    VersionComparator comparator = new VersionComparator();

    if (globalDependencies != null) {
      for (DependencyGraph graph : globalDependencies) {
        Map<String, String> map = graph.getHighestVersionMap();
        for (String key : map.keySet()) {
          String newVersion = map.get(key);
          String oldVersion = latestArtifacts.get(key);
          if (oldVersion == null || comparator.compare(newVersion, oldVersion) > 0) {
            latestArtifacts.put(key, map.get(key));
          }
        }
      }
    }
    return latestArtifacts;
  }

  /**
   * Returns the number of rows in {@code table} that show unavailable ({@code null} result) or some
   * failures for {@code columnName}.
   */
  public static long countFailures(List<ArtifactResults> table, String columnName) {
    return table.stream()
        .filter(row -> row.getResult(columnName) == null || row.getFailureCount(columnName) > 0)
        .count();
  }

  private static final int MINIMUM_NUMBER_DEPENDENCY_PATHS = 5;

  /**
   * Returns mapping from jar files to summaries of the root problem in their {@link
   * DependencyPath}s. The summary explains common patterns ({@code groupId:artifactId}) in the path
   * elements. The returned map does not have a key for a jar file when it has fewer than {@link
   * #MINIMUM_NUMBER_DEPENDENCY_PATHS} dependency paths or a common pattern is not found among the
   * elements in the paths.
   *
   * <p>Example summary: "Artifacts 'com.google.http-client:google-http-client &gt;
   * commons-logging:commons-logging &gt; log4j:log4j' exist in all 994 dependency paths. Example
   * path: com.google.cloud:google-cloud-core:1.59.0 ..."
   *
   * <p>Using this summary in the BOM dashboard avoids repetitive items in the {@link
   * DependencyPath} list that share the same root problem caused by widely-used libraries, for
   * example, {@code commons-logging:commons-logging}, {@code
   * com.google.http-client:google-http-client} and {@code log4j:log4j}.
   */
  private static ImmutableMap<String, String> findRootCauses(ClassPathResult classPathResult) {
    // Freemarker is not good at handling non-string keys. Path object in .ftl is automatically
    // converted to String. https://freemarker.apache.org/docs/app_faq.html#faq_nonstring_keys
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    for (ClassPathEntry entry : classPathResult.getClassPath()) {
      List<DependencyPath> dependencyPaths = classPathResult.getDependencyPaths(entry);

      ImmutableList<String> commonVersionlessArtifacts =
          commonVersionlessArtifacts(dependencyPaths);

      if (dependencyPaths.size() > MINIMUM_NUMBER_DEPENDENCY_PATHS
          && commonVersionlessArtifacts.size() > 1) { // The last paths elements are always same
        builder.put(
            entry.toString(),
            summaryMessage(
                dependencyPaths.size(), commonVersionlessArtifacts, dependencyPaths.get(0)));
      }
    }
    return builder.build();
  }

  private static ImmutableList<String> commonVersionlessArtifacts(
      List<DependencyPath> dependencyPaths) {
    ImmutableList<String> initialVersionlessCoordinates =
        versionlessCoordinates(dependencyPaths.get(0));
    // LinkedHashSet remembers insertion order
    LinkedHashSet<String> versionlessCoordinatesIntersection =
        Sets.newLinkedHashSet(initialVersionlessCoordinates);
    for (DependencyPath dependencyPath : dependencyPaths) {
      // List of versionless coordinates ("groupId:artifactId")
      ImmutableList<String> versionlessCoordinatesInPath = versionlessCoordinates(dependencyPath);
      // intersection of elements in DependencyPaths
      versionlessCoordinatesIntersection.retainAll(versionlessCoordinatesInPath);
    }

    return ImmutableList.copyOf(versionlessCoordinatesIntersection);
  }

  private static ImmutableList<String> versionlessCoordinates(DependencyPath dependencyPath) {
    return dependencyPath.getArtifacts().stream()
        .map(Artifacts::makeKey)
        .collect(toImmutableList());
  }

  private static String summaryMessage(
      int dependencyPathCount, List<String> coordinates, DependencyPath examplePath) {
    StringBuilder messageBuilder = new StringBuilder();
    messageBuilder.append("Dependency path '");
    messageBuilder.append(Joiner.on(" > ").join(coordinates));
    messageBuilder.append("' exists in all " + dependencyPathCount + " dependency paths. ");
    messageBuilder.append("Example path: ");
    messageBuilder.append(examplePath);
    return messageBuilder.toString();
  }
}
