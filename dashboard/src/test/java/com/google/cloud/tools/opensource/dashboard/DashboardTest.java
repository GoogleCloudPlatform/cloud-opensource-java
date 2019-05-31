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
import nu.xom.XPathContext;
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
import com.google.common.collect.Streams;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;

public class DashboardTest {

  private static final Correspondence<Node, String> NODE_VALUES =
      Correspondence.from((node, expected) ->
          trimAndCollapseWhiteSpace(node.getValue())
          .equals(expected), "has value equal to");

  private static String trimAndCollapseWhiteSpace(String value) {
    return CharMatcher.whitespace().trimAndCollapseFrom(value, ' ');
  }

  private static Path outputDirectory;
  private static Builder builder = new Builder();
  private static Document dashboard;
  private static Document details;

  @BeforeClass
  public static void setUp() throws IOException, ParsingException {
    // Creates "dashboard.html" and artifact reports in outputDirectory
    try {
      outputDirectory = DashboardMain.generate("com.google.cloud:libraries-bom:1.0.0");
    } catch (Throwable t) {
      t.printStackTrace();
      Assert.fail("Could not generate dashboard");
    }

    dashboard = parseOutputFile("dashboard.html");
    details = parseOutputFile("artifact_details.html");
  }

  @AfterClass
  public static void cleanUp() {
    try {
      // Mac's APFS fails with InsecureRecursiveDeleteException without ALLOW_INSECURE.
      // Still safe as this test does not use symbolic links
      if (outputDirectory != null) {
        MoreFiles.deleteRecursively(outputDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
      }
    } catch (IOException ex) {
      // no big deal
    }
  }
  
  @Test // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/617
  public void testPlural() {
    Nodes statisticItems = dashboard.query("//section[@class='statistics']/div/div");
    for (Node node : statisticItems) {
      Node h2 = node.query("h2").get(0);
      Node span = node.query("span").get(0);
      int errorCount = Integer.parseInt(h2.getValue());
      if (errorCount == 1) {
        String message = span.getValue();
        Assert.assertEquals("Has Linkage Errors", message);
      }
    }
    
    Assert.assertFalse(dashboard.toXML().contains("1 HAVE"));
  }

  @Test
  public void testHeader() {
    Nodes h1 = dashboard.query("//h1");
    Assert.assertEquals(1, h1.size());
    Assert.assertEquals("Dependency Status of com.google.cloud:libraries-bom:1.0.0",
        h1.get(0).getValue());
  }
  
  @Test
  public void testSvg() {
    XPathContext context = new XPathContext("svg", "http://www.w3.org/2000/svg");
    Nodes svg = dashboard.query("//svg:svg", context);
    Assert.assertEquals(4, svg.size());
  }

  @Test
  public void testCss() {
    Path dashboardCss = outputDirectory.resolve("dashboard.css");
    Assert.assertTrue(Files.exists(dashboardCss));
    Assert.assertTrue(Files.isRegularFile(dashboardCss));
  }

  private static Document parseOutputFile(String fileName)
      throws IOException, ParsingException {
    Path html = outputDirectory.resolve(fileName);
    Assert.assertTrue("Could not find a regular file for " + fileName,
        Files.isRegularFile(html));
    Assert.assertTrue("The file is not readable: " + fileName, Files.isReadable(html));

    try (InputStream source = Files.newInputStream(html)) {
      return builder.build(source);
    }
  }
  
  @Test
  public void testArtifactDetails() throws IOException, ArtifactDescriptorException {
    Artifact bom = new DefaultArtifact("com.google.cloud:libraries-bom:1.0.0");
    List<Artifact> artifacts = RepositoryUtility.readBom(bom).getManagedDependencies();
    Assert.assertTrue("Not enough artifacts found", artifacts.size() > 1);

    Assert.assertEquals("en-US", dashboard.getRootElement().getAttribute("lang").getValue());

    Nodes tr = details.query("//tr");
    Assert.assertEquals(artifacts.size() + 1, tr.size()); // header row adds 1
    for (int i = 1; i < tr.size(); i++) { // start at 1 to skip header row
      Nodes td = tr.get(i).query("td");
      Assert.assertEquals(Artifacts.toCoordinates(artifacts.get(i - 1)), td.get(0).getValue());
      for (int j = 1; j < 5; ++j) { // start at 1 to skip the leftmost artifact coordinates column
        assertValidCellValue((Element) td.get(j));
      }
    }
    Nodes href = details.query("//tr/td[@class='artifact-name']/a/@href");
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
            ex.getLineNumber() + ", column " + ex.getColumnNumber() + "\r\n";
        message += ex.getMessage() + "\r\n";
        message += new String(data, StandardCharsets.UTF_8);
        Assert.fail(message);
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
  public void testDashboard_statisticBox() {
    Nodes artifactCount =
        dashboard.query("//div[@class='statistic-item statistic-item-green']/h2");
    Assert.assertTrue(artifactCount.size() > 0);
    for (Node artifactCountElement : artifactCount) {
      String value = artifactCountElement.getValue().trim();
      Assert.assertTrue(value, Integer.parseInt(value) > 0);
    }
  }

  @Test
  public void testLinkageReports() {
    Nodes reports = details.query("//p[@class='jar-linkage-report']");
    // appengine-api-sdk, shown as first item in linkage errors, has these errors
    Truth.assertThat(trimAndCollapseWhiteSpace(reports.get(0).getValue()))
        .isEqualTo(
            "106 target classes causing linkage errors referenced from 516 source classes.");

    Nodes dependencyPaths = details.query(
        "//p[@class='linkage-check-dependency-paths'][position()=last()]");
    Node dependencyPathMessage = dependencyPaths.get(0);
    Assert.assertEquals(
        "The following paths to the jar file from the BOM are found in the dependency tree:",
        trimAndCollapseWhiteSpace(dependencyPathMessage.getValue()));
    int dependencyPathListSize =
        details.query("//ul[@class='linkage-check-dependency-paths']/li").size();
    Truth.assertWithMessage("The dashboard should not show repetitive dependency paths")
        .that(dependencyPathListSize)
        .isLessThan(100);
  }

  @Test
  public void testDashboard_recommendedCoordinates() {
    Nodes recommendedListItem = dashboard.query("//ul[@id='recommended']/li");
    Assert.assertTrue(recommendedListItem.size() > 100);

    // fails if these are not valid Maven coordinates
    for (Node node : recommendedListItem) {
      new DefaultArtifact(node.getValue());
    }

    ImmutableList<String> coordinateList =
        Streams.stream(recommendedListItem).map(Node::getValue).collect(toImmutableList());
    
    ArrayList<String> sorted = new ArrayList<>(coordinateList);
    Comparator<String> comparator = new SortWithoutVersion();
    Collections.sort(sorted, comparator);

    for (int i = 0; i < sorted.size(); i++) {
      Assert.assertEquals(
          "Coordinates are not sorted: ", sorted.get(i), coordinateList.get(i));
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

  @Test
  public void testDashboard_unstableDependencies() {
    // Pre 1.0 version section
    Nodes unstable = dashboard.query("//ul[@id='unstable']/li");
    Assert.assertTrue(unstable.size() > 1);
    for (int i = 0; i < unstable.size(); i++) {
      String value = unstable.get(i).getValue();
      Assert.assertTrue(value, value.contains(":0"));
    }

    // This element appears only when every dependency becomes stable
    Nodes stable = dashboard.query("//p[@id='stable-notice']");
    Assert.assertEquals(0, stable.size());
  }

  @Test
  public void testDashboard_lastUpdatedField() {
    Nodes updated = dashboard.query("//p[@id='updated']");
    Assert.assertEquals(
        "Could not find updated field: " + dashboard.toXML(), 1, updated.size());
  }

  @Test
  public void testComponent_linkageCheckResult() throws IOException, ParsingException {
    Document document = parseOutputFile(
        "com.google.http-client_google-http-client-appengine_1.29.1.html");
    Nodes reports = document.query("//p[@class='jar-linkage-report']");
    Assert.assertEquals(1, reports.size());
    Truth.assertThat(trimAndCollapseWhiteSpace(reports.get(0).getValue()))
        .isEqualTo(
            "106 target classes causing linkage errors referenced from 516 source classes.");
    Nodes causes = document.query("//p[@class='jar-linkage-report-cause']");
    Truth.assertWithMessage("google-http-client-appengine should show linkage errors for RpcStubDescriptor")
        .that(causes)
        .comparingElementsUsing(NODE_VALUES)
        .contains(
            "com.google.net.rpc3.client.RpcStubDescriptor is not found,"
                + " referenced from 21 source classes ▶"); // '▶' is the toggle button
  }


  @Test
  public void testComponent_success() throws IOException, ParsingException {
    Document document = parseOutputFile(
        "com.google.api.grpc_proto-google-common-protos_1.14.0.html");
    Nodes greens = document.query("//h3[@style='color: green']");
    Assert.assertTrue(greens.size() >= 2);
    Nodes presDependencyMediation =
        document.query("//pre[@class='suggested-dependency-mediation']");
    // There's a pre tag for dependency
    Assert.assertEquals(1, presDependencyMediation.size());

    Nodes presDependencyTree = document.query("//p[@class='dependency-tree-node']");
    Assert.assertTrue(
        "Dependency Tree should be shown in dashboard", presDependencyTree.size() > 0);
  }

  @Test
  public void testComponent_failure() throws IOException, ParsingException {
    Document document = parseOutputFile(
        "com.google.api.grpc_grpc-google-common-protos_1.14.0.html");
    Nodes greens = document.query("//h3[@style='color: green']");
    Assert.assertEquals(0, greens.size());
    Nodes reds = document.query("//h3[@style='color: red']");
    Assert.assertEquals(3, reds.size());
    Nodes presDependencyMediation =
        document.query("//pre[@class='suggested-dependency-mediation']");
    Assert.assertTrue(
        "For failed component, suggested dependency should be shown",
        presDependencyMediation.size() >= 1);
    Nodes dependencyTree = document.query("//p[@class='dependency-tree-node']");
    Assert.assertTrue(
        "Dependency Tree should be shown in dashboard even when FAILED",
        dependencyTree.size() > 0);
  }

  @Test
  public void testLinkageErrorsInProvidedDependency() throws IOException, ParsingException {
    // google-http-client-appengine has provided dependency to (problematic) appengine-api-1.0-sdk
    Document document = parseOutputFile(
        "com.google.http-client_google-http-client-appengine_1.29.1.html");
    Nodes linkageCheckMessages = document.query("//ul[@class='jar-linkage-report-cause']/li");
    Truth.assertThat(linkageCheckMessages.size()).isGreaterThan(0);
    Truth.assertThat(linkageCheckMessages.get(0).getValue())
        .contains("com.google.appengine.api.appidentity.AppIdentityServicePb");
  }

  @Test
  public void testZeroLinkageErrorShowsZero() throws IOException, ParsingException {
    // grpc-auth does not have a linkage error, and it should show zero in the section
    Document document = parseOutputFile("io.grpc_grpc-auth_1.20.0.html");
    Nodes linkageErrorsTotal = document.query("//p[@id='linkage-errors-total']");
    Truth.assertThat(linkageErrorsTotal.size()).isEqualTo(1);
    Truth.assertThat(linkageErrorsTotal.get(0).getValue())
        .contains("0 linkage error(s)");
  }

  @Test
  public void testGlobalUpperBoundUpgradeMessage() throws IOException, ParsingException {
    // Case 1: BOM needs to be updated
    Document document = parseOutputFile("com.google.protobuf_protobuf-java-util_3.6.1.html");
    Nodes globalUpperBoundBomUpgradeNodes =
        document.query("//li[@class='global-upper-bound-bom-upgrade']");
    Truth.assertThat(globalUpperBoundBomUpgradeNodes.size()).isEqualTo(1);
    String bomUpgradeMessage = globalUpperBoundBomUpgradeNodes.get(0).getValue();
    Truth.assertThat(bomUpgradeMessage).contains(
        "Upgrade com.google.protobuf:protobuf-java-util:jar:3.6.1 in the BOM to version \"3.7.1\"");

    // Case 2: Dependency needs to be updated
    Nodes globalUpperBoundDependencyUpgradeNodes =
        document.query("//li[@class='global-upper-bound-dependency-upgrade']");
    Truth.assertThat(globalUpperBoundDependencyUpgradeNodes.size()).isEqualTo(2);
    String dependencyUpgradeMessage = globalUpperBoundDependencyUpgradeNodes.get(0).getValue();
    Truth.assertThat(dependencyUpgradeMessage).contains(
        "Upgrade com.google.guava:guava:jar:19.0 to version \"27.1-android\"");
  }
}
