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
import java.util.Map.Entry;
import java.util.TreeMap;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.DependencyResolutionException;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyTreeFormatter;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.cloud.tools.opensource.dependencies.Update;
import com.google.cloud.tools.opensource.dependencies.VersionComparator;
import com.google.common.annotations.VisibleForTesting;

public class DashboardMain {
  public static final String TEST_NAME_UPPER_BOUND = "Upper Bounds";
  public static final String TEST_NAME_DEPENDENCY_CONVERGENCE = "Dependency Convergence";

  public static void main(String[] args)
      throws IOException, TemplateException, ArtifactDescriptorException {

    Path output = generate();
    System.out.println("Wrote dashboard into " + output.toAbsolutePath());
  }

  public static Path generate()
      throws IOException, TemplateException, ArtifactDescriptorException {
    Configuration configuration = configureFreemarker();

    Path relativePath = Paths.get("target", "dashboard");
    Path output = Files.createDirectories(relativePath);

    DefaultArtifact bom =
        new DefaultArtifact("com.google.cloud:cloud-oss-bom:pom:0.66.0-SNAPSHOT");
    List<Artifact> managedDependencies = RepositoryUtility.readBom(bom);

    Map<Artifact, ArtifactInfo> cache = loadArtifactInfo(managedDependencies);
    
    List<ArtifactResults> table = generateReports(configuration, output, cache);
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
  static List<ArtifactResults> generateReports(Configuration configuration, Path output,
      Map<Artifact, ArtifactInfo> artifacts) {

    List<ArtifactResults> table = new ArrayList<>();
    for (Entry<Artifact, ArtifactInfo> entry : artifacts.entrySet()) {
      ArtifactInfo info = entry.getValue();
      try {
        if (info.getException() != null) {
          ArtifactResults unavailable = new ArtifactResults(entry.getKey());
          unavailable.setExceptionMessage(info.getException().getMessage());
          table.add(unavailable);
        } else {
          ArtifactResults results =
              generateReport(configuration, output, entry.getKey(), entry.getValue());
          table.add(results);
        }
      } catch (RepositoryException | IOException ex) {
        ArtifactResults unavailableTestResult = new ArtifactResults(entry.getKey());
        unavailableTestResult.setExceptionMessage(ex.getMessage());
        // Even when there's problem generating test result, show the error in the dashboard
        table.add(unavailableTestResult);
      } catch (TemplateException ex) {
        // This failure is ours. No need to report it in dashboard for an artifact
        throw new RuntimeException("Error in template setting in this project", ex);
      }
    }

    return table;
  }
  
  // TODO this is really ugly but avoids reparsing the graph.
  // Need to think about better factoring here. Maybe parse the graph higher up
  // and pass the results into the report generating methods.
  private static List<DependencyGraph> globalDependencies;
  
  private static Map<Artifact, ArtifactInfo> loadArtifactInfo(List<Artifact> artifacts) {
    Map<Artifact, ArtifactInfo> artifactCache = new LinkedHashMap<>();
    if (globalDependencies == null) {
      globalDependencies = new ArrayList<>();
    }
    
    for (Artifact artifact : artifacts) {
      try {
        DependencyGraph completeDependencies =
            DependencyGraphBuilder.getCompleteDependencies(artifact);
        globalDependencies.add(completeDependencies);
        
        // picks versions according to Maven rules
        DependencyGraph transitiveDependencies =
            DependencyGraphBuilder.getTransitiveDependencies(artifact);
  
        ArtifactInfo info = new ArtifactInfo(completeDependencies, transitiveDependencies);
        artifactCache.put(artifact, info);
      } catch (DependencyCollectionException | DependencyResolutionException ex) {
        ArtifactInfo info = new ArtifactInfo(ex);
        artifactCache.put(artifact, info);
      }
    }
    
    return artifactCache;
  }

  private static ArtifactResults generateReport(Configuration configuration, Path output,
      Artifact artifact, ArtifactInfo artifactInfo) throws IOException, TemplateException,
      DependencyCollectionException, DependencyResolutionException {

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
          findUpperBoundsFailures(completeDependencies, transitiveDependencies);

      String dependencyTree =
          DependencyTreeFormatter.formatDependencyPaths(completeDependencies.list());
      Template report = configuration.getTemplate("/templates/component.ftl");

      Map<String, Object> templateData = new HashMap<>();
      templateData.put("groupId", artifact.getGroupId());
      templateData.put("artifactId", artifact.getArtifactId());
      templateData.put("version", artifact.getVersion());
      templateData.put("updates", convergenceIssues);
      templateData.put("upperBoundFailures", upperBoundFailures);
      templateData.put("dependencyTree", dependencyTree);
      report.process(templateData, out);

      ArtifactResults results = new ArtifactResults(artifact);
      results.addResult(TEST_NAME_UPPER_BOUND, upperBoundFailures.size());
      results.addResult(TEST_NAME_DEPENDENCY_CONVERGENCE, convergenceIssues.size());

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
    
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(dashboardFile), StandardCharsets.UTF_8)) {
      Template dashboard = configuration.getTemplate("/templates/dashboard.ftl");
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("table", table);
      templateData.put("lastUpdated", LocalDateTime.now());
      templateData.put("latestArtifacts", latestArtifacts);

      dashboard.process(templateData, out);
    }
  }
}
