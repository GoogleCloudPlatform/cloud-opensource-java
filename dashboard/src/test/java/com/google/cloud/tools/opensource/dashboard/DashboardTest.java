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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

import freemarker.template.TemplateException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.common.truth.Truth;

public class DashboardTest {

  private static Path outputDirectory;
  private Builder builder = new Builder();

  @BeforeClass
  public static void setUp() throws ArtifactDescriptorException, IOException, TemplateException {
    // Creates "dashboard.html" in outputDirectory
    outputDirectory = DashboardMain.generate();
  }

  @AfterClass
  public static void cleanUp() throws IOException {
    MoreFiles.deleteRecursively(outputDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  @Test
  public void testMain() throws IOException, TemplateException, ArtifactDescriptorException {
    // Ensuring normal execution doesn't cause any exception
    DashboardMain.main(null);
  }

  @Test
  public void testDashboard()
      throws IOException, TemplateException, ParsingException, ArtifactDescriptorException {
    Assert.assertTrue(Files.exists(outputDirectory));
    Assert.assertTrue(Files.isDirectory(outputDirectory));

    Path dashboardHtml = outputDirectory.resolve("dashboard.html");
    Assert.assertTrue(Files.isRegularFile(dashboardHtml));
    
    DefaultArtifact bom =
        new DefaultArtifact("com.google.cloud:cloud-oss-bom:pom:0.62.0-SNAPSHOT");
    List<Artifact> artifacts = RepositoryUtility.readBom(bom);
    Assert.assertTrue("Not enough artifacts found", artifacts.size() > 1);

    try (InputStream source = Files.newInputStream(dashboardHtml)) {
      Document document = builder.build(dashboardHtml.toFile());

      Assert.assertEquals("en-US", document.getRootElement().getAttribute("lang").getValue());

      Nodes tr = document.query("//tr");
      Assert.assertEquals(artifacts.size() + 1, tr.size()); // header row adds 1
      for (int i = 1; i < tr.size(); i++) { // start at 1 to skip header row
        Nodes td = tr.get(i).query("td");
        Assert.assertEquals(Artifacts.toCoordinates(artifacts.get(i - 1)), td.get(0).getValue());
        Element firstResult = (Element) (td.get(1));
        Truth.assertThat(firstResult.getValue()).isAnyOf("PASS", "FAIL");
        Truth.assertThat(firstResult.getAttributeValue("class")).isAnyOf("PASS", "FAIL");

        Element secondResult = (Element) (td.get(2));
        Truth.assertThat(secondResult.getValue()).isAnyOf("PASS", "FAIL");
        Truth.assertThat(secondResult.getAttributeValue("class")).isAnyOf("PASS", "FAIL");
      }
      Nodes href = document.query("//tr/td/a/@href");
      for (int i = 0; i < href.size(); i++) {
        String fileName = href.get(i).getValue();
        Assert.assertEquals(Artifacts.toCoordinates(artifacts.get(i)).replace(':', '_') + ".html", 
            URLDecoder.decode(fileName, "UTF-8"));
        Path componentReport = outputDirectory.resolve(fileName);
        Assert.assertTrue(fileName + " is missing", Files.isRegularFile(componentReport));
        try {
          Document report = builder.build(componentReport.toFile());
          Assert.assertEquals("en-US", report.getRootElement().getAttribute("lang").getValue());
        } catch (ParsingException ex) {
          byte[] data = Files.readAllBytes(componentReport);
          String message = "Could not parse " + componentReport + " at line " +
            ex.getLineNumber() +", column " + ex.getColumnNumber() + "\r\n";
          message += ex.getMessage() + "\r\n";
          message += new String(data, StandardCharsets.UTF_8);
          Assert.fail(message);
        }
      }

      Nodes updated = document.query("//p[@id='updated']");
      Assert.assertEquals("didn't find updated" + document.toXML(), 1, updated.size());
    }
  }

  @Test
  public void testComponent_success() throws IOException, ValidityException, ParsingException {
    Path successHtml = outputDirectory.resolve(
        "com.google.api.grpc_proto-google-common-protos_1.12.0.html");
    Assert.assertTrue(Files.isRegularFile(successHtml));

    try (InputStream source = Files.newInputStream(successHtml)) {
      Document document = builder.build(successHtml.toFile());
      Nodes greens = document.query("//h3[@style='color: green']");
      Assert.assertTrue(greens.size() >= 2);
      Nodes pres = document.query("//pre");
      Assert.assertEquals(0, pres.size());
    }
  }

  @Test
  public void testComponent_failure() throws IOException, ValidityException, ParsingException {
    Path failureHtml = outputDirectory.resolve(
        "com.google.api.grpc_grpc-google-common-protos_1.12.0.html");
    Assert.assertTrue(Files.isRegularFile(failureHtml));

    try (InputStream source = Files.newInputStream(failureHtml)) {
      Document document = builder.build(failureHtml.toFile());
      Nodes greens = document.query("//h3[@style='color: green']");
      Assert.assertEquals(0, greens.size());
      Nodes pres = document.query("//pre");
      Assert.assertTrue(pres.size() >= 1);
    }
  }
}
