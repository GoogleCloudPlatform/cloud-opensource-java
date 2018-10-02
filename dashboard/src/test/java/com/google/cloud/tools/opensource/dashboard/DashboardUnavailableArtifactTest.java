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

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.common.truth.Truth;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DashboardUnavailableArtifactTest {

  private static Path outputDirectory;
  private Builder builder = new Builder();

  @BeforeClass
  public static void setUp() throws IOException {
    outputDirectory = Files.createDirectories(Paths.get("target", "dashboard"));
  }

  @AfterClass
  public static void cleanUp() throws IOException {
    MoreFiles.deleteRecursively(outputDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
  }
  @Test
  public void testDashboardForRepositoryException() {
    Configuration configuration = DashboardMain.configureFreemarker();
    Artifact validArtifact = new DefaultArtifact("io.grpc:grpc-context:1.15.0");
    Artifact nonExistentArtifact = new DefaultArtifact("io.grpc:nonexistent:jar:1.15.0");

    List<ArtifactResults> artifactResults =
        DashboardMain.generateReports(
            configuration, outputDirectory, Arrays.asList(validArtifact, nonExistentArtifact));

    Assert.assertEquals(
        "The length fo the ArtifactResults should match the length of artifacts",
        2,
        artifactResults.size());
    Assert.assertEquals(
        "The first artifact result should be valid",
        true,
        artifactResults.get(0).getResult(DashboardMain.TEST_NAME_UPPER_BOUND));
    ArtifactResults errorArtifactResult = artifactResults.get(1);
    Assert.assertNull(
        "The second artifact result should be null",
        errorArtifactResult.getResult(DashboardMain.TEST_NAME_UPPER_BOUND));
    Assert.assertEquals(
        "The error artifact result should contain error message",
        "Could not find artifact io.grpc:nonexistent:jar:1.15.0 in central (http://repo1.maven.org/maven2/)",
        errorArtifactResult.getExceptionMessage());
  }

  @Test
  public void testDashboardWithRepositoryException()
      throws IOException, TemplateException, ParsingException {
    Configuration configuration = DashboardMain.configureFreemarker();

    Artifact validArtifact = new DefaultArtifact("io.grpc:grpc-context:1.15.0");
    ArtifactResults validArtifactResult = new ArtifactResults(validArtifact);
    validArtifactResult.addResult(DashboardMain.TEST_NAME_UPPER_BOUND, true);
    validArtifactResult.addResult(DashboardMain.TEST_NAME_DEPENDENCY_CONVERGENCE, true);

    Artifact invalidArtifact = new DefaultArtifact("io.grpc:nonexistent:jar:1.15.0");
    ArtifactResults errorArtifactResult = new ArtifactResults(invalidArtifact);
    errorArtifactResult.setExceptionMessage(
        "Could not find artifact io.grpc:nonexistent:jar:1.15.0 in central (http://repo1.maven.org/maven2/)");
    List<ArtifactResults> table = new ArrayList<>();
    table.add(validArtifactResult);
    table.add(errorArtifactResult);

    DashboardMain.generateDashboard(configuration, outputDirectory, table);

    Path generatedDashboardHtml = outputDirectory.resolve("dashboard.html");
    Assert.assertTrue(Files.isRegularFile(generatedDashboardHtml));
    Document document = builder.build(generatedDashboardHtml.toFile());
    Assert.assertEquals("en-US", document.getRootElement().getAttribute("lang").getValue());
    Nodes tr = document.query("//tr");

    Assert.assertEquals(
        "The size of rows in table should match the number of artifacts + 1 (header)",
        tr.size(),table.size() + 1);

    Nodes tdForValidArtifact = tr.get(1).query("td");
    Assert.assertEquals(
        Artifacts.toCoordinates(validArtifact), tdForValidArtifact.get(0).getValue());
    Element firstResult = (Element) (tdForValidArtifact.get(1));
    Truth.assertThat(firstResult.getValue()).isEqualTo("PASS");
    Truth.assertThat(firstResult.getAttributeValue("class")).isEqualTo("PASS");

    Nodes tdForErrorArtifact = tr.get(2).query("td");
    Assert.assertEquals(
        Artifacts.toCoordinates(invalidArtifact), tdForErrorArtifact.get(0).getValue());
    Element secondResult = (Element) (tdForErrorArtifact.get(1));
    Truth.assertThat(secondResult.getValue()).isEqualTo("UNAVAILABLE");
    Truth.assertThat(secondResult.getAttributeValue("class")).isEqualTo("UNAVAILABLE");
  }
}
