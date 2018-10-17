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

import static com.google.cloud.tools.opensource.dashboard.DashboardMain.GOOGLE_CLOUD_OSS_BOM;
import static com.google.cloud.tools.opensource.dashboard.DashboardMain.configureFreemarker;
import static com.google.cloud.tools.opensource.dashboard.DashboardMain.copyCss;

import com.google.cloud.tools.opensource.classpath.FullyQualifiedMethodSignature;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
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
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

/**
 * This tool is to generate a table to show the result of {@link
 * com.google.cloud.tools.opensource.classpath.StaticLinkageChecker} for each possible pair from
 * artifacts in Cloud OSS BOM.
 */
public class BomRoundRobinTableMain {

  public static void main(String[] args)
      throws IOException, TemplateException, ArtifactDescriptorException {
    Path output = generate();
    System.out.println("Wrote dashboard into " + output.toAbsolutePath());
  }

  private static final class TableKey {
    private final String artifact1Coordinate;
    private final String artifact2Coordinate;

    TableKey(String artifact1Coordinate, String artifact2Coordinate) {
      this.artifact1Coordinate = artifact1Coordinate;
      this.artifact2Coordinate = artifact2Coordinate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TableKey)) {
        return false;
      }
      TableKey tableKey = (TableKey) o;
      return Objects.equals(artifact1Coordinate, tableKey.artifact1Coordinate)
          && Objects.equals(artifact2Coordinate, tableKey.artifact2Coordinate);
    }

    @Override
    public int hashCode() {
      return Objects.hash(artifact1Coordinate, artifact2Coordinate);
    }
  }

  static Path generate() throws ArtifactDescriptorException, IOException, TemplateException {
    DefaultArtifact bom = new DefaultArtifact(GOOGLE_CLOUD_OSS_BOM);
    List<Artifact> managedDependencies = RepositoryUtility.readBom(bom);
    Path relativeOutputPath = Paths.get("target", "round-robin-table");
    Path outputDirectoryPath = Files.createDirectories(relativeOutputPath);
    return generateRoundRobinTable(outputDirectoryPath, managedDependencies);
  }

  static Path generateRoundRobinTable(Path outputDirectory, List<Artifact> artifacts)
      throws IOException, TemplateException {
    Path outputFilePath = outputDirectory.resolve("index.html");
    Map<TableKey, List<FullyQualifiedMethodSignature>> linkageCheckTable =
        buildRoundRobinLinkageCheckTable(artifacts);
    Configuration freemarkerConfiguration = configureFreemarker();
    Template report = freemarkerConfiguration.getTemplate("/templates/round-robin-table.ftl");
    Map<String, Object> templateData = new HashMap<>();
    templateData.put("artifactList", artifacts);
    templateData.put("lastUpdated", LocalDateTime.now());
    templateData.put("table", linkageCheckTable);
    try (Writer out =
        new OutputStreamWriter(
            new FileOutputStream(outputFilePath.toFile()), StandardCharsets.UTF_8)) {
      report.process(templateData, out);
    }
    copyCss(outputDirectory);
    return outputFilePath;
  }

  static Map<TableKey, List<FullyQualifiedMethodSignature>> buildRoundRobinLinkageCheckTable(
      List<Artifact> dependencies) {
    Map<TableKey, List<FullyQualifiedMethodSignature>> table = new HashMap<>();
    for (Artifact artifact1 : dependencies) {
      for (Artifact artifact2 : dependencies) {
        String artifact1Coordinate =  Artifacts.toCoordinates(artifact1);
        String artifact2Coordinate =  Artifacts.toCoordinates(artifact2);

        // TODO: Add logic to call StaticLinkageChecker for the class path from the 2 artifacts
        table.put(new TableKey(artifact1Coordinate, artifact2Coordinate),
            new ArrayList<>());
      }
    }
    return table;
  }

}
