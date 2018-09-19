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

import freemarker.core.ParseException;
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
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.DependencyResolutionException;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.annotations.VisibleForTesting;

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
    List<Artifact> managedDependencies = readBom(bom);
    
    List<String> coordinateList =
        managedDependencies.stream().map(Artifacts::toCoordinates).collect(Collectors.toList());
    
    generateDashboard(configuration, output, coordinateList);
    generateReports(configuration, output, managedDependencies);
    
    return output;
  }

  private static Configuration configureFreemarker() {
    Configuration configuration = new Configuration(new Version("2.3.28"));
    configuration.setDefaultEncoding("UTF-8");
    configuration.setClassForTemplateLoading(DashboardMain.class, "/");
    return configuration;
  }

  private static void generateReports(Configuration configuration, Path output,
      List<Artifact> artifacts) throws ArtifactDescriptorException {
    for (Artifact artifact : artifacts ) {
      try {
        generateReport(configuration, output, artifact);
      } catch (DependencyCollectionException | DependencyResolutionException | IOException
          | TemplateException ex) {
        // TODO logger
        System.err.println("Error generating report for " + artifact);
        System.err.println(ex.getMessage());
      }
    }
  }

  /**
   * Parse the dependencyManagement section of an artifact and return the
   * artifacts included there.
   */
  @VisibleForTesting
  // TODO pull out to utility class. When we do this, we need to consider the
  // possibility that the artifact is not a BOM; that is, that it does not have
  // a dependency management section.
  static List<Artifact> readBom(Artifact artifact) throws ArtifactDescriptorException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);

    ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
    request.addRepository(RepositoryUtility.CENTRAL);
    request.setArtifact(artifact);

    ArtifactDescriptorResult resolved = system.readArtifactDescriptor(session, request);
    List<Exception> exceptions = resolved.getExceptions();
    if (!exceptions.isEmpty()) {
      throw new ArtifactDescriptorException(resolved, exceptions.get(0).getMessage());
    }
    
    List<Artifact> managedDependencies = new ArrayList<>();
    for (Dependency dependency : resolved.getManagedDependencies()) {
      Artifact managed = dependency.getArtifact();
      // TODO remove this hack once we get these out of 
      // google-cloud-java's BOM
      if (managed.getArtifactId().equals("google-cloud-logging-logback")
          || managed.getArtifactId().equals("google-cloud-contrib")) {
        continue;
      }
      // String coords = Artifacts.toCoordinates(managed);
      if (!managedDependencies.contains(artifact)) {
        managedDependencies.add(artifact);
      } else {
        System.err.println("Duplicate dependency " + dependency);
      }
    }
    return managedDependencies;
  }

  private static void generateReport(Configuration configuration, Path output, Artifact artifact)
      throws ParseException, IOException, TemplateException, DependencyCollectionException,
      DependencyResolutionException {
    
    String coordinates = Artifacts.toCoordinates(artifact);
    File outputFile = output.resolve(coordinates.replace(':', '_') + ".html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {

      DependencyGraph graph =
          DependencyGraphBuilder.getCompleteDependencies(artifact);
      List<String> updates = graph.findUpdates();
   
      Template report = configuration.getTemplate("/templates/component.ftl");

      Map<String, Object> templateData = new HashMap<>();
      templateData.put("groupId", artifact.getGroupId());
      templateData.put("artifactId", artifact.getArtifactId());
      templateData.put("version", artifact.getVersion());
      templateData.put("updates", updates);
      report.process(templateData, out);

      out.flush();
    }
  }

  private static void generateDashboard(Configuration configuration, Path output,
      List<String> artifacts) throws ParseException, IOException, TemplateException {
    
    File dashboardFile = output.resolve("dashboard.html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(dashboardFile), StandardCharsets.UTF_8)) {
      Template dashboard = configuration.getTemplate("/templates/dashboard.ftl");
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("artifacts", artifacts);
      templateData.put("lastUpdated", LocalDateTime.now());

      dashboard.process(templateData, out);
      out.flush();
    }
  }
  
}
