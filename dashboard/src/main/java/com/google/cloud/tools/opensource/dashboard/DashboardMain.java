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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// TODO this is leaking aether too far
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.common.base.Splitter;

public class DashboardMain {
  
  // todo move this to config file
  static String[] ARTIFACTS = {
      "com.google.cloud:google-cloud-core:1.41.0",
      "com.google.cloud:google-cloud-datastore:1.41.0"
  };
  
  public static void main(String[] args) 
      throws IOException, TemplateException {

    generate();
  }

  public static Path generate() throws IOException, TemplateException {
    Configuration configuration = configure();
    
    Path relativePath = Paths.get("target", "dashboard");
    Path output = Files.createDirectories(relativePath);

    generateDashboard(configuration, output);
    generateReports(configuration, output);
    
    return output;
  }

  private static Configuration configure() {
    Configuration configuration = new Configuration(new Version("2.3.28"));
    configuration.setDefaultEncoding("UTF-8");
    configuration.setClassForTemplateLoading(DashboardMain.class, "/");
    return configuration;
  }

  private static void generateReports(Configuration configuration, Path output) {
    for (String coordinates : ARTIFACTS) {
      try {
        generateReport(configuration, output, coordinates);
      } catch (DependencyCollectionException | DependencyResolutionException | IOException
          | TemplateException ex) {
        // TODO logger
        System.err.println("Error generating report for " + coordinates);
        System.err.println(ex.getMessage());
      }
    }
  }

  private static void generateReport(Configuration configuration, Path output, String coordinates)
      throws ParseException, IOException, TemplateException, DependencyCollectionException,
      DependencyResolutionException {
    
    List<String> coords = Splitter.on(":").splitToList(coordinates);  
    String groupId = coords.get(0);
    String artifactId = coords.get(1);
    String version = coords.get(2);
    
    File outputFile = output.resolve(coordinates.replace(':', '_') + ".html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {

      DependencyGraph graph =
          DependencyGraphBuilder.getCompleteDependencies(groupId, artifactId, version);
      List<String> updates = graph.findUpdates();
            
      Template report = configuration.getTemplate("/templates/component.ftl");

      Map<String, Object> templateData = new HashMap<>();
      templateData.put("groupId", groupId);
      templateData.put("artifactId", artifactId);
      templateData.put("version", version);
      templateData.put("updates", updates);
      report.process(templateData, out);

      out.flush();
    }
  }

  private static void generateDashboard(Configuration configuration, Path output)
      throws ParseException, IOException, TemplateException {
    File dashboardFile = output.resolve("dashboard.html").toFile();
    try (Writer out = new OutputStreamWriter(
        new FileOutputStream(dashboardFile), StandardCharsets.UTF_8)) {
      Template dashboard = configuration.getTemplate("/templates/dashboard.ftl");
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("artifacts", ARTIFACTS);
      templateData.put("lastUpdated", LocalDateTime.now());

      dashboard.process(templateData, out);
      out.flush();
    }
  }
  
}
