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

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.DependencyMediation;
import com.google.cloud.tools.opensource.classpath.GradleDependencyMediation;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.LinkageProblem;
import com.google.cloud.tools.opensource.dashboard.DashboardArguments.DependencyMediationAlgorithm;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.cloud.tools.opensource.dependencies.Update;
import com.google.cloud.tools.opensource.dependencies.VersionComparator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.version.InvalidVersionSpecificationException;

public class DashboardMain {

  public static final String TEST_NAME_LINKAGE_CHECK = "Linkage Errors";
  public static final String TEST_NAME_UPPER_BOUND = "Upper Bounds";
  public static final String TEST_NAME_GLOBAL_UPPER_BOUND = "Global Upper Bounds";
  public static final String TEST_NAME_DEPENDENCY_CONVERGENCE = "Dependency Convergence";
  private static final int LATEST_BOM_VERSION_COUNT = 30;

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
      generateLatestVersions(
          dashboardArguments.getVersionlessCoordinates(),
          dashboardArguments.getDependencyMediation());
    } else if (dashboardArguments.hasFile()) {
      generate(dashboardArguments.getBomFile(), dashboardArguments.getDependencyMediation());
    } else {
      generate(dashboardArguments.getBomCoordinates(), dashboardArguments.getDependencyMediation());
    }
  }

  private static void generateLatestVersions(
      String versionlessCoordinates, DependencyMediationAlgorithm dependencyMediationAlgorithm)
      throws IOException, TemplateException, RepositoryException, URISyntaxException,
          MavenRepositoryException {
    List<String> elements = Splitter.on(':').splitToList(versionlessCoordinates);
    if (elements.size() != 2) {
      System.err.println(
          "Versionless coordinates should have one colon: " + versionlessCoordinates);
      return;
    }
    String groupId = elements.get(0);
    String artifactId = elements.get(1);

    RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();

    // New versions come last
    ImmutableList<String> allVersions =
        RepositoryUtility.findVersions(repositorySystem, groupId, artifactId);
    // Because previous versions in Maven Central not change, no need to regenerate dashboards
    // for old BOM versions. This avoids timeout in nightly Kokoro job.
    ImmutableList<String> latestNVersions = allVersions.subList(
        Math.max(allVersions.size() - LATEST_BOM_VERSION_COUNT, 0), allVersions.size());
    for (String version : latestNVersions) {
      generate(
          String.format("%s:%s:%s", groupId, artifactId, version), dependencyMediationAlgorithm);
    }
    generateVersionIndex(groupId, artifactId, allVersions);
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
  static Path generate(
      String bomCoordinates, DependencyMediationAlgorithm dependencyMediationAlgorithm)
      throws IOException, TemplateException, RepositoryException, URISyntaxException {
    Path output = generate(Bom.readBom(bomCoordinates), dependencyMediationAlgorithm);
    System.out.println("Wrote dashboard for " + bomCoordinates + " to " + output);
    return output;
  }

  @VisibleForTesting
  static Path generate(Path bomFile, DependencyMediationAlgorithm dependencyMediationAlgorithm)
      throws IOException, TemplateException, URISyntaxException, MavenRepositoryException,
          InvalidVersionSpecificationException {
    checkArgument(Files.isRegularFile(bomFile), "The input BOM %s is not a regular file", bomFile);
    checkArgument(Files.isReadable(bomFile), "The input BOM %s is not readable", bomFile);
    Path output = generate(Bom.readBom(bomFile), dependencyMediationAlgorithm);
    System.out.println("Wrote dashboard for " + bomFile + " to " + output);
    return output;
  }

  private static Path generate(Bom bom, DependencyMediationAlgorithm dependencyMediationAlgorithm)
      throws IOException, TemplateException, URISyntaxException,
          InvalidVersionSpecificationException {

    ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();

    DependencyMediation dependencyMediation =
        dependencyMediationAlgorithm == DependencyMediationAlgorithm.MAVEN
            ? DependencyMediation.MAVEN
            : GradleDependencyMediation.withEnforcedPlatform(bom);

    ClassPathResult classPathResult =
        classPathBuilder.resolve(managedDependencies, false, dependencyMediation);
    ImmutableList<ClassPathEntry> classpath = classPathResult.getClassPath();

    LinkageChecker linkageChecker = LinkageChecker.create(classpath);

    ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

    ArtifactCache cache = loadArtifactInfo(managedDependencies);
    Path output = generateHtml(bom, cache, classPathResult, linkageProblems);

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
      ImmutableSet<LinkageProblem> linkageProblems)
      throws IOException, TemplateException, URISyntaxException {

    Artifact bomArtifact = new DefaultArtifact(bom.getCoordinates());

    Path relativePath =
        outputDirectory(
            bomArtifact.getGroupId(), bomArtifact.getArtifactId(), bomArtifact.getVersion());
    Path output = Files.createDirectories(relativePath);

    copyResource(output, "css/dashboard.css");
    copyResource(output, "js/dashboard.js");

    ImmutableMap<ClassPathEntry, ImmutableSet<LinkageProblem>> linkageProblemTable =
        indexByJar(linkageProblems);

    List<ArtifactResults> table =
        generateReports(
            freemarkerConfiguration, output, cache, linkageProblemTable, classPathResult, bom);

    generateDashboard(
        freemarkerConfiguration,
        output,
        table,
        cache.getGlobalDependencies(),
        linkageProblemTable,
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
      ImmutableMap<ClassPathEntry, ImmutableSet<LinkageProblem>> linkageProblemTable,
      ClassPathResult classPathResult,
      Bom bom)
      throws TemplateException {

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
          Map<ClassPathEntry, ImmutableSet<LinkageProblem>> relevantLinkageProblemTable =
              Maps.filterKeys(linkageProblemTable, jarsInDependencyTree::contains);

          ArtifactResults results =
              generateArtifactReport(
                  configuration,
                  output,
                  artifact,
                  entry.getValue(),
                  cache.getGlobalDependencies(),
                  ImmutableMap.copyOf(relevantLinkageProblemTable),
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
          dependencyGraphBuilder.buildVerboseDependencyGraph(artifact);
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
      ImmutableMap<ClassPathEntry, ImmutableSet<LinkageProblem>> linkageProblemTable,
      ClassPathResult classPathResult,
      Bom bom)
      throws IOException, TemplateException {

    String coordinates = Artifacts.toCoordinates(artifact);
    File outputFile = output.resolve(coordinates.replace(':', '_') + ".html").toFile();

    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {

      // includes all versions
      DependencyGraph graph = artifactInfo.getCompleteDependencies();
      List<Update> convergenceIssues = graph.findUpdates();

      // picks versions according to Maven rules
      DependencyGraph transitiveDependencies = artifactInfo.getTransitiveDependencies();

      Map<Artifact, Artifact> upperBoundFailures =
          findUpperBoundsFailures(graph.getHighestVersionMap(), transitiveDependencies);

      Map<Artifact, Artifact> globalUpperBoundFailures = findUpperBoundsFailures(
          collectLatestVersions(globalDependencies), transitiveDependencies);

      long totalLinkageErrorCount =
          linkageProblemTable.values().stream()
              .flatMap(problemToClasses -> problemToClasses.stream().map(LinkageProblem::getSymbol))
              .distinct() // The dashboard counts linkage errors by the symbols
              .count();

      Template report = configuration.getTemplate("/templates/component.ftl");

      Map<String, Object> templateData = new HashMap<>();

      DefaultObjectWrapper wrapper =
          new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_28).build();
      TemplateHashModel staticModels = wrapper.getStaticModels();
      templateData.put("linkageProblem", staticModels.get(LinkageProblem.class.getName()));

      templateData.put("artifact", artifact);
      templateData.put("updates", convergenceIssues);
      templateData.put("upperBoundFailures", upperBoundFailures);
      templateData.put("globalUpperBoundFailures", globalUpperBoundFailures);
      templateData.put("lastUpdated", LocalDateTime.now());
      templateData.put("dependencyGraph", graph);
      templateData.put("linkageProblems", linkageProblemTable);
      templateData.put("classPathResult", classPathResult);
      templateData.put("totalLinkageErrorCount", totalLinkageErrorCount);
      templateData.put("coordinates", bom.getCoordinates());

      report.process(templateData, out);

      ArtifactResults results = new ArtifactResults(artifact);
      results.addResult(TEST_NAME_UPPER_BOUND, upperBoundFailures.size());
      results.addResult(TEST_NAME_GLOBAL_UPPER_BOUND, globalUpperBoundFailures.size());
      results.addResult(TEST_NAME_DEPENDENCY_CONVERGENCE, convergenceIssues.size());
      results.addResult(TEST_NAME_LINKAGE_CHECK, (int) totalLinkageErrorCount);

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
   * Partitions {@code linkageProblems} by the JAR file that contains the {@link ClassFile}.
   *
   * <p>For example, {@code classes = result.get(JarX).get(linkageProblemY)} where {@code classes}
   * are not null means that {@code JarX} has {@code linkageProblemY} and that {@code JarX} contains
   * {@code classes} which reference {@code linkageProblemY.getSymbol()}.
   */
  private static ImmutableMap<ClassPathEntry, ImmutableSet<LinkageProblem>> indexByJar(
      ImmutableSet<LinkageProblem> linkageProblems) {

    ImmutableMap<ClassPathEntry, Collection<LinkageProblem>> jarMap =
        Multimaps.index(linkageProblems, problem -> problem.getSourceClass().getClassPathEntry())
            .asMap();

    return ImmutableMap.copyOf(Maps.transformValues(jarMap, ImmutableSet::copyOf));
  }

  @VisibleForTesting
  static void generateDashboard(
      Configuration configuration,
      Path output,
      List<ArtifactResults> table,
      List<DependencyGraph> globalDependencies,
      ImmutableMap<ClassPathEntry, ImmutableSet<LinkageProblem>> linkageProblemTable,
      ClassPathResult classPathResult,
      Bom bom)
      throws IOException, TemplateException {

    Map<String, String> latestArtifacts = collectLatestVersions(globalDependencies);

    Map<String, Object> templateData = new HashMap<>();
    templateData.put("table", table);
    templateData.put("lastUpdated", LocalDateTime.now());
    templateData.put("latestArtifacts", latestArtifacts);
    templateData.put("linkageProblems", linkageProblemTable);
    templateData.put("classPathResult", classPathResult);
    templateData.put("dependencyPathRootCauses", findRootCauses(classPathResult));
    templateData.put("coordinates", bom.getCoordinates());
    templateData.put("dependencyGraphs", globalDependencies);

    // Accessing static methods from Freemarker template
    // https://freemarker.apache.org/docs/pgui_misc_beanwrapper.html#autoid_60
    DefaultObjectWrapper wrapper = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_28)
        .build();
    TemplateHashModel staticModels = wrapper.getStaticModels();
    templateData.put("dashboardMain", staticModels.get(DashboardMain.class.getName()));
    templateData.put("pieChart", staticModels.get(PieChart.class.getName()));
    templateData.put("linkageProblem", staticModels.get(LinkageProblem.class.getName()));

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
    try (Writer out =
        new OutputStreamWriter(new FileOutputStream(dependencyTrees), StandardCharsets.UTF_8)) {
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
        dependencyPaths.get(0).getArtifactKeys();
    // LinkedHashSet remembers insertion order
    LinkedHashSet<String> versionlessCoordinatesIntersection =
        Sets.newLinkedHashSet(initialVersionlessCoordinates);
    for (DependencyPath dependencyPath : dependencyPaths) {
      // List of versionless coordinates ("groupId:artifactId")
      ImmutableList<String> versionlessCoordinatesInPath = dependencyPath.getArtifactKeys();
      // intersection of elements in DependencyPaths
      versionlessCoordinatesIntersection.retainAll(versionlessCoordinatesInPath);
    }

    return ImmutableList.copyOf(versionlessCoordinatesIntersection);
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
