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
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.DependencyResolutionException;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.cloud.tools.opensource.dependencies.Update;
import com.google.cloud.tools.opensource.dependencies.VersionComparator;

public class DashboardMain {
  
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

  private static Configuration configureFreemarker() {
    Configuration configuration = new Configuration(new Version("2.3.28"));
    configuration.setDefaultEncoding("UTF-8");
    configuration.setClassForTemplateLoading(DashboardMain.class, "/");
    return configuration;
  }

  private static List<ArtifactResults> generateReports(Configuration configuration, Path output,
      List<Artifact> artifacts) {
    
    List<ArtifactResults> table = new ArrayList<>();

    for (Artifact artifact : artifacts ) {
      try {
        ArtifactResults results = generateReport(configuration, output, artifact);
        table.add(results);
      } catch (DependencyCollectionException | DependencyResolutionException | IOException
          | TemplateException ex) {
        // TODO the dashboard should somehow show that it failed to generate this report;
        // not just silently drop it from the index
        System.err.println("Error generating report for " + artifact);
        System.err.println(ex.getMessage());
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

      List<String> upperBoundFailures =
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
      // TODO the keys/report names probably belong in named constants somewhere
      results.addResult("Upper Bounds", upperBoundFailures.size() == 0);
      results.addResult("Dependency Convergence", convergenceIssues.size() == 0);
      
      return results;
    }
  }

  // TODO may want to push this into DependencyGraph. However this probably first
  // needs some caching of the graphs so we don't end up traversing the dependency graph
  // extra times.
  private static List<String> findUpperBoundsFailures(DependencyGraph graph,
      DependencyGraph transitiveDependencies) {
    Map<String, String> expectedVersionMap = graph.getHighestVersionMap();
    Map<String, String> actualVersionMap = transitiveDependencies.getHighestVersionMap();
    
    VersionComparator comparator = new VersionComparator();
    
    List<String> upperBoundFailures = new ArrayList<>(); 
    for (String id : expectedVersionMap.keySet()) {
      String expectedVersion = expectedVersionMap.get(id);
      String actualVersion = actualVersionMap.get(id);
      // Check that the actual version is not null because it is 
      // possible for dependencies to appear or disappear from the tree
      // depending on which version of another dependency is loaded.
      // In both cases, no action is needed.
      if (actualVersion != null && comparator.compare(actualVersion, expectedVersion) < 0) {
        // Maven did not choose highest version
        upperBoundFailures.add("Upgrade " + id + ":" + actualVersion + " to " + expectedVersion);
      }
    }
    return upperBoundFailures;
  }

  private static void generateDashboard(Configuration configuration, Path output,
      List<ArtifactResults> table) throws IOException, TemplateException {
    
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
