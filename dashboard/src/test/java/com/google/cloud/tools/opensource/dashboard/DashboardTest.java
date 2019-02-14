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

import static com.google.common.collect.ImmutableList.toImmutableList;

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
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;

public class DashboardTest {

  private static final Correspondence<Node, String> NODE_VALUES =
      new Correspondence<Node, String>() {
        @Override
        public boolean compare(Node node, String expected) {
          String trimmedValue = trimAndCollapseWhiteSpaces(node.getValue());
          return trimmedValue.equals(expected);
        }

        @Override
        public String toString() {
          return "has value equal to";
        }
      };

  private static String trimAndCollapseWhiteSpaces(String value) {
    return CharMatcher.whitespace().trimAndCollapseFrom(value, ' ');
  }

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
      Assert.fail("Could not generate dashboard");
    }
  }

  @AfterClass
  public static void cleanUp() {
    try {
      MoreFiles.deleteRecursively(outputDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
    } catch (IOException ex) {
      // no big deal
    }
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
        for (int j = 1; j < 5; ++j) {
          assertValidCellValue((Element) td.get(j));
        }
      }
      Nodes href = document.query("//tr/td[@class='artifact-name']/a/@href");
      for (int i = 0; i < href.size(); i++) {
        String fileName = href.get(i).getValue();
        Artifact artifact = artifacts.get(i);
        Assert.assertEquals(
            Artifacts.toCoordinates(artifact).replace(':', '_') + ".html",
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

      // TODO(#439): these should all be separate tests for the different components
      Nodes reports = document.query("//p[@class='jar-linkage-report']");
      // grpc-testing-1.17.1, shown as first item in linkage errors, has these errors
      Truth.assertThat(trimAndCollapseWhiteSpaces(reports.get(0).getValue()))
          .isEqualTo("3 target classes causing linkage errors referenced from 3 source classes.");

      Nodes li = document.query("//ul[@id='recommended']/li");
      Assert.assertTrue(li.size() > 100);

      List<String> coordinateList =
          toList(li).stream().map(Node::getValue).collect(toImmutableList());
      // fails if these are not valid Maven coordinates
      coordinateList.forEach(DefaultArtifact::new);

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
      
      Nodes stable = document.query("//p[@id='stable-notice']");
      Assert.assertEquals(0, stable.size());

      Nodes artifactCount = document
          .query("//div[@class='statistic-item statistic-item-green']/h2");
      Assert.assertTrue(artifactCount.size() > 0);
      for (Node artifactCountElement : toList(artifactCount)) {
        String value = artifactCountElement.getValue().trim();
        Assert.assertTrue(value, Integer.parseInt(value) > 0);
      }
    }
  }

  private static void assertValidCellValue(Element cellElement) {
    String cellValue = cellElement.getValue().replaceAll("\\s", "");
    Truth.assertThat(cellValue).containsMatch("PASS|\\d+FAILURES?");
    Truth.assertWithMessage("It should not use plural for 1 item").that(cellValue)
        .doesNotContainMatch("1 FAILURES");
    Truth.assertThat(cellElement.getAttributeValue("class")).isAnyOf("pass", "fail");
  }

  @Test
  public void testComponent_staticLinkageCheckResult() throws IOException, ParsingException {
    Path grpcAltsHtml = outputDirectory.resolve("io.grpc_grpc-alts_1.17.1.html");
    Assert.assertTrue(Files.isRegularFile(grpcAltsHtml));

    try (InputStream source = Files.newInputStream(grpcAltsHtml)) {
      Document document = builder.build(source);

      Nodes reports = document.query("//p[@class='jar-linkage-report']");
      Assert.assertEquals(1, reports.size());
      Truth.assertThat(trimAndCollapseWhiteSpaces(reports.get(0).getValue()))
          .isEqualTo("2 target classes causing linkage errors referenced from 2 source classes.");
      Nodes causes = document.query("//p[@class='jar-linkage-report-cause']");
      Truth.assertWithMessage("grpc-alts should show linkage errors for CommunicatorServer")
          .that(toList(causes))
          .comparingElementsUsing(NODE_VALUES)
          .contains("com.sun.jdmk.comm.CommunicatorServer is not found, referenced from");
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

      Nodes presDependencyTree = document.query("//p[@class='dependency-tree-node']");
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
      Nodes dependencyTree = document.query("//p[@class='dependency-tree-node']");
      Assert.assertTrue("Dependency Tree should be shown in dashboard even when FAILED",
          dependencyTree.size() > 0);
    }
  }

  @Test
  public void testLinkageErrorsUnderProvidedDependency() throws IOException, ParsingException {
    // google-cloud-translate has transitive dependency to (problematic) appengine-api-1.0-sdk
    // The path to appengine-api-1.0-sdk includes scope:provided dependency
    Path googleCloudTranslateHtml =
        outputDirectory.resolve("com.google.cloud_google-cloud-translate_1.59.0.html");
    Assert.assertTrue(Files.isRegularFile(googleCloudTranslateHtml));

    try (InputStream source = Files.newInputStream(googleCloudTranslateHtml)) {
      Document document = builder.build(source);
      Nodes staticLinkageCheckMessage =
          document.query("//ul[@class='jar-linkage-report-cause']/li");
      Truth.assertThat(staticLinkageCheckMessage.size()).isGreaterThan(0);
      Truth.assertThat(staticLinkageCheckMessage.get(0).getValue())
          .contains("com.google.appengine.api.appidentity.AppIdentityServicePb");
    }
  }

  @Test
  public void testZeroLinkageErrorShowsZero() throws IOException, ParsingException {
    // grpc-auth does not have a linkage error, and it should show zero in the section
    Path zeroLinkageErrorHtml = outputDirectory.resolve("io.grpc_grpc-auth_1.17.1.html");
    Assert.assertTrue(Files.isRegularFile(zeroLinkageErrorHtml));

    try (InputStream source = Files.newInputStream(zeroLinkageErrorHtml)) {
      Document document = builder.build(source);
      Nodes staticLinkageTotalMessage = document.query("//p[@id='static-linkage-errors-total']");
      Truth.assertThat(staticLinkageTotalMessage.size()).isEqualTo(1);
      Truth.assertThat(staticLinkageTotalMessage.get(0).getValue())
          .contains("0 static linkage error(s)");
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

  private static ImmutableList<Node> toList(Nodes nodes) {
    ImmutableList.Builder<Node> builder = ImmutableList.builder();
    for (int i = 0; i < nodes.size(); i++) {
      builder.add(nodes.get((i)));
    }
    return builder.build();
  }
}
