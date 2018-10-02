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

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.cloud.tools.opensource.dependencies.Update;
import com.google.cloud.tools.opensource.dependencies.VersionComparator;
import com.google.common.annotations.VisibleForTesting;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.DependencyResolutionException;

public class DashboardMain {
  public static final String TEST_NAME_UPPER_BOUND = "Upper Bounds";
  public static final String TEST_NAME_DEPENDENCY_CONVERGENCE = "Dependency Convergence";

  public static void main(String[] args)
      throws IOException, TemplateException, ArtifactDescriptorException {

    Path output = generate();
    System.out.println("Wrote dashboard into " + output.toAbsolutePath());
  }

  public static Path generate() throws IOException, TemplateException, ArtifactDescriptorException {
    Configuration configuration = configureFreemarker();

    Path relativePath = Paths.get("target", "dashboard");
    Path output = Files.createDirectories(relativePath);

    DefaultArtifact bom =
        new DefaultArtifact("com.google.cloud:cloud-oss-bom:pom:0.62.0-SNAPSHOT");
    List<Artifact> managedDependencies = RepositoryUtility.readBom(bom);

    List<ArtifactResults> table = generateReports(configuration, output, managedDependencies);
    generateDashboard(configuration, output, table);

    return output;
  }

  @VisibleForTesting
  static Configuration configureFreemarker() {
    Configuration configuration = new Configuration(new Version("2.3.28"));
    configuration.setDefaultEncoding("UTF-8");
    configuration.setClassForTemplateLoading(DashboardMain.class, "/");
    return configuration;
  }

  @VisibleForTesting
  static List<ArtifactResults> generateReports( Configuration configuration, Path output,
      List<Artifact> artifacts) {

    List<ArtifactResults> table = new ArrayList<>();

    for (Artifact artifact : artifacts) {
      try {
        ArtifactResults results = generateReport(configuration, output, artifact);
        table.add(results);
      } catch (RepositoryException | IOException | TemplateException ex) {
        ArtifactResults unavailableTestResult = new ArtifactResults(artifact);
        unavailableTestResult.setExceptionMessage(ex.getMessage());
        // Even when there's problem generating test result, show the error in the dashboard
        table.add(unavailableTestResult);
      }
    }

    return table;
  }

  private static ArtifactResults generateReport(Configuration configuration, Path output,
      Artifact artifact) throws IOException, TemplateException, DependencyCollectionException,
          DependencyResolutionException {

    String coordinates = Artifacts.toCoordinates(artifact);
    File outputFile = output.resolve(coordinates.replace(':', '_') + ".html").toFile();

    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {

      // includes all versions
      DependencyGraph completeDependencies =
          DependencyGraphBuilder.getCompleteDependencies(artifact);
      List<Update> convergenceIssues = completeDependencies.findUpdates();

      // picks versions according to Maven rules
      DependencyGraph transitiveDependencies =
          DependencyGraphBuilder.getTransitiveDependencies(artifact);

      Map<Artifact, Artifact> upperBoundFailures =
          findUpperBoundsFailures(completeDependencies, transitiveDependencies);

      Template report = configuration.getTemplate("/templates/component.ftl");

      Map<String, Object> templateData = new HashMap<>();
      templateData.put("groupId", artifact.getGroupId());
      templateData.put("artifactId", artifact.getArtifactId());
      templateData.put("version", artifact.getVersion());
      templateData.put("updates", convergenceIssues);
      templateData.put("upperBoundFailures", upperBoundFailures);
      report.process(templateData, out);

      ArtifactResults results = new ArtifactResults(artifact);
      results.addResult(TEST_NAME_UPPER_BOUND, upperBoundFailures.size() == 0);
      results.addResult(TEST_NAME_DEPENDENCY_CONVERGENCE, convergenceIssues.size() == 0);

      return results;
    }
  }

  // TODO may want to push this into DependencyGraph. However this probably first
  // needs some caching of the graphs so we don't end up traversing the dependency graph
  // extra times.
  private static Map<Artifact, Artifact> findUpperBoundsFailures(DependencyGraph graph,
      DependencyGraph transitiveDependencies) {
    Map<String, String> expectedVersionMap = graph.getHighestVersionMap();
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
        // upperBoundFailures.add("Upgrade " + id + ":" + actualVersion + " to " + expectedVersion);
        DefaultArtifact lower = new DefaultArtifact(id + ":" + actualVersion);
        DefaultArtifact upper = new DefaultArtifact(id + ":" + expectedVersion);
        upperBoundFailures.put(lower, upper);
      }
    }
    return upperBoundFailures;
  }

  @VisibleForTesting
  static void generateDashboard(Configuration configuration, Path output,
      List<ArtifactResults> table)
      throws IOException, TemplateException {
    File dashboardFile = output.resolve("dashboard.html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(dashboardFile), StandardCharsets.UTF_8)) {
      Template dashboard = configuration.getTemplate("/templates/dashboard.ftl");
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("table", table);
      templateData.put("lastUpdated", LocalDateTime.now());

      dashboard.process(templateData, out);
    }
  }
}
