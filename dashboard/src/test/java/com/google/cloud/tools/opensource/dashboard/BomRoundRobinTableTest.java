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
 *
 *
 */

package com.google.cloud.tools.opensource.dashboard;

import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class BomRoundRobinTableTest {
  private static Path outputDirectory;
  private Builder builder = new Builder();

  @BeforeClass
  public static void setUp() throws IOException {
    outputDirectory = Paths.get("target", "round-robin-table");
    Files.createDirectories(outputDirectory);
  }

  @AfterClass
  public static void cleanUp() throws IOException {
    //MoreFiles.deleteRecursively(outputDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  @Test
  public void testRoundRobinTableGeneration()
      throws IOException, TemplateException, ParsingException {
    List<Artifact> artifacts = Arrays.asList(
        new DefaultArtifact("io.grpc:grpc-auth:1.15.1"),
        new DefaultArtifact("com.google.api.grpc:grpc-google-iam-v1:0.12.0"),
        new DefaultArtifact("com.google.cloud:google-cloud-container:0.67.0-beta")
    );
    Path outputTableFilePath =
        BomRoundRobinTableMain.generateRoundRobinTable(outputDirectory, artifacts);

    Assert.assertTrue(Files.isReadable(outputTableFilePath));

    try (InputStream inputForGeneratedHtmlFile = Files.newInputStream(outputTableFilePath)) {
      Document document = builder.build(inputForGeneratedHtmlFile);
      Nodes tr = document.query("//tr");
      Assert.assertEquals(
          "3-element artifact list should generate 3+1 rows in the table including header row",
          4,
          tr.size());
      Nodes td = document.query("//tr/td[@class='round-robin-table-cell']");
      Assert.assertEquals(
          "3-element artifact list should generate 3x3 results in the table", 9, td.size());
    }
  }

}
