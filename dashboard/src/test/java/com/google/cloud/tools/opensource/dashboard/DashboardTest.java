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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

import freemarker.template.TemplateException;

import org.eclipse.aether.RepositoryException;
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
  public static void setUp()
      throws IOException, TemplateException, RepositoryException, ClassNotFoundException {
    // Creates "dashboard.html" in outputDirectory
    try {
      outputDirectory = DashboardMain.generate();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  @AfterClass
  public static void cleanUp() throws IOException {
    MoreFiles.deleteRecursively(outputDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  @Test
  public void testCss()
      throws IOException, TemplateException, ParsingException, ArtifactDescriptorException {
    Path dashboardCss = outputDirectory.resolve("dashboard.css");
    Assert.assertTrue(Files.exists(dashboardCss));
    Assert.assertTrue(Files.isRegularFile(dashboardCss));
  }

  @Test
  public void testDashboard()
      throws IOException, TemplateException, ParsingException, ArtifactDescriptorException {
    Assert.assertTrue(Files.exists(outputDirectory));
    Assert.assertTrue(Files.isDirectory(outputDirectory));

    Path dashboardHtml = outputDirectory.resolve("dashboard.html");
    Assert.assertTrue(Files.isRegularFile(dashboardHtml));
    
    DefaultArtifact bom =
        new DefaultArtifact("com.google.cloud:cloud-oss-bom:pom:1.0.0-SNAPSHOT");
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
        Truth.assertThat(firstResult.getValue().replaceAll("\\s", ""))
            .containsMatch("PASS|\\d+FAILURES?");
        Truth.assertThat(firstResult.getAttributeValue("class")).isAnyOf("PASS", "FAIL");

        Element secondResult = (Element) (td.get(2));
        Truth.assertThat(secondResult.getValue().replaceAll("\\s", ""))
            .containsMatch("PASS|\\d+FAILURES?");
        Truth.assertThat(secondResult.getAttributeValue("class")).isAnyOf("PASS", "FAIL");

        Element thirdResult = (Element) (td.get(3));
        Truth.assertThat(thirdResult.getValue().replaceAll("\\s", ""))
            .containsMatch("PASS|\\d+FAILURES?");
        Truth.assertThat(thirdResult.getAttributeValue("class")).isAnyOf("PASS", "FAIL");

        Element fourthResult = (Element) (td.get(4));
        Truth.assertThat(fourthResult.getValue().replaceAll("\\s", ""))
            .containsMatch("PASS|\\d+FAILURES?");
        Truth.assertThat(fourthResult.getAttributeValue("class")).isAnyOf("PASS", "FAIL");
      }
      Nodes href = document.query("//tr/td[@class='artifact-name']/a/@href");
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

      // TODO these should all be separate tests for the different components
      Node linkage = document.query("//pre[@id='static_linkage_errors']").get(0);
      Assert.assertFalse(linkage.getValue().contains("(0 errors)"));

      Nodes li = document.query("//ul[@id='recommended']/li");
      Assert.assertTrue(li.size() > 100);
      ArrayList<String> coordinateList = new ArrayList<>();

      for (int i = 0; i < li.size(); i++) {
        String coordinates = li.get(i).getValue();
        // fails if these are not valid Maven coordinates
        new DefaultArtifact(coordinates);
        coordinateList.add(coordinates);
      }
      
      ArrayList<String> sorted = new ArrayList<>(coordinateList);
      Comparator<String> comparator = new SortWithoutVersion();
      Collections.sort(sorted, comparator);
      
      for (int i = 0; i < sorted.size(); i++) {
        Assert.assertEquals("Coordinates are not sorted: ", sorted.get(i),
            coordinateList.get(i));
      }
      
      Nodes unstable = document.query("//ul[@id='unstable']/li");
      Assert.assertTrue(unstable.size() > 1);
      for (int i = 0; i < unstable.size(); i++) {
        String value = unstable.get(i).getValue();
        Assert.assertTrue(value, value.contains(":0"));
      }      
      
      Nodes updated = document.query("//p[@id='updated']");
      Assert.assertEquals("didn't find updated" + document.toXML(), 1, updated.size());
      
      Nodes stable = document.query("//p[@id='stable_notice']");
      Assert.assertEquals(0, stable.size());
      
      Nodes artifactCount = document.query("//h2[@class='artifactcount']");
      Assert.assertTrue(artifactCount.size() > 0);
      for (int i = 0; i < artifactCount.size(); i++) {
        String value = artifactCount.get(i).getValue();
        Assert.assertTrue(value, Integer.parseInt(value) > 0);
      }            
    }
  }

  @Test
  public void testComponent_staticLinkageCheckResult() throws IOException, ParsingException {
    Path grpcAltsHtml = outputDirectory.resolve("io.grpc_grpc-alts_1.17.1.html");
    Assert.assertTrue(Files.isRegularFile(grpcAltsHtml));

    try (InputStream source = Files.newInputStream(grpcAltsHtml)) {
      Document document = builder.build(source);

      Nodes staticLinkageCheckMessage = document.query("//p[@id='static-linkage-check']");
      Assert.assertEquals(1, staticLinkageCheckMessage.size());
      Truth.assertThat(staticLinkageCheckMessage.get(0).getValue())
          .contains("static linkage error(s)");

      Nodes jarLinkageReportNode = document.query("//pre[@class='jar-linkage-report']");
      boolean foundGrpcCoreError = false;
      for (int i = 0; i < jarLinkageReportNode.size(); i++) {
        if (jarLinkageReportNode.get(i).getValue().contains("grpc-core-1.17.1.jar")) {
          foundGrpcCoreError = true;
        }
      }
      Assert.assertFalse(foundGrpcCoreError);
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
      Nodes presDependencyMediation =
          document.query("//pre[@class='suggested-dependency-mediation']");
      // There's a pre tag for dependency
      Assert.assertEquals(1, presDependencyMediation.size());

      Nodes presDependencyTree = document.query("//p[@class='DEPENDENCY_TREE_NODE']");
      Assert.assertTrue("Dependency Tree should be shown in dashboard",
          presDependencyTree.size() > 0);
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
      Nodes reds = document.query("//h3[@style='color: red']");
      Assert.assertEquals(3, reds.size());
      Nodes presDependencyMediation =
          document.query("//pre[@class='suggested-dependency-mediation']");
      Assert.assertTrue("For failed component, suggested dependency should be shown",
          presDependencyMediation.size() >= 1);
      Nodes dependencyTree = document.query("//p[@class='DEPENDENCY_TREE_NODE']");
      Assert.assertTrue("Dependency Tree should be shown in dashboard even when FAILED",
          dependencyTree.size() > 0);
    }
  }
  
  private static class SortWithoutVersion implements Comparator<String> {

    @Override
    public int compare(String s1, String s2) {  
      s1 = s1.substring(0, s1.lastIndexOf(':'));
      s2 = s2.substring(0, s2.lastIndexOf(':'));
      return s1.compareTo(s2);
    }

  }

}
