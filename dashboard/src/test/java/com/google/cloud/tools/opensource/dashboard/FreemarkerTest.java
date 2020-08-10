/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassNotFoundProblem;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.ClassSymbol;
import com.google.cloud.tools.opensource.classpath.LinkageProblem;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.common.truth.Truth;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests for FreeMarker logic without reading any JAR files. 
 */
public class FreemarkerTest {

  private static Path outputDirectory;
  private static ImmutableMap<ClassPathEntry, ImmutableSet<LinkageProblem>> symbolProblemTable;

  private Builder builder = new Builder();

  @BeforeClass
  public static void setUpDirectory() throws IOException {
    outputDirectory = Files.createDirectories(Paths.get("target", "dashboard"));
  }

  @Before
  public void setUp() {
    Artifact artifact = new DefaultArtifact("com.google:foo:1.0.0")
        .setFile(new File("foo/bar-1.2.3.jar"));
    ClassPathEntry entry = new ClassPathEntry(artifact);
    ImmutableSet<LinkageProblem> dummyProblems =
        ImmutableSet.of(
            new ClassNotFoundProblem(
                new ClassFile(entry, "abc.def.G"), new ClassSymbol("com.foo.Bar")));
    symbolProblemTable = ImmutableMap.of(entry, dummyProblems);
  }

  @AfterClass
  public static void cleanUp() throws IOException {
    // Mac's APFS fails with InsecureRecursiveDeleteException without ALLOW_INSECURE.
    // Still safe as this test does not use symbolic links
    MoreFiles.deleteRecursively(outputDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  @Test
  public void testCountFailures() throws IOException, TemplateException, ParsingException {
    Configuration configuration = DashboardMain.configureFreemarker();

    Artifact artifact1 = new DefaultArtifact("io.grpc:grpc-context:1.15.0");
    ArtifactResults results1 = new ArtifactResults(artifact1);
    results1.addResult("Linkage Errors", 56);

    Artifact artifact2 = new DefaultArtifact("grpc:grpc:1.15.0");
    ArtifactResults results2 = new ArtifactResults(artifact2);
    results2.addResult("Linkage Errors", 0);

    List<ArtifactResults> table = ImmutableList.of(results1, results2);
    List<DependencyGraph> globalDependencies = ImmutableList.of();
    DashboardMain.generateDashboard(
        configuration,
        outputDirectory,
        table,
        globalDependencies,
        symbolProblemTable,
        new ClassPathResult(LinkedListMultimap.create(), ImmutableList.of()),
        new Bom("mock:artifact:1.6.7", null));

    Path dashboardHtml = outputDirectory.resolve("index.html");
    Assert.assertTrue(Files.isRegularFile(dashboardHtml));
    Document document = builder.build(dashboardHtml.toFile());

    // xom's query cannot specify partial class field, e.g., 'statistic-item'
    Nodes counts = document.query("//div[@class='container']/div/h2");
    Assert.assertTrue(counts.size() > 0);
    for (int i = 0; i < counts.size(); i++) {
      Integer.parseInt(counts.get(i).getValue().trim());
    }
    // Linkage Errors
    Truth.assertThat(counts.get(1).getValue().trim()).isEqualTo("1");
  }

  @Test
  public void testVersionIndex()
      throws IOException, TemplateException, URISyntaxException, ParsingException {
    Path output =
        DashboardMain.generateVersionIndex(
            "com.google.cloud",
            "libraries-bom",
            ImmutableList.of("1.0.0", "2.0.0", "2.1.0-SNAPSHOT"));
    Assert.assertTrue(
        output.endsWith(Paths.get("target", "com.google.cloud", "libraries-bom", "index.html")));
    Assert.assertTrue(Files.isRegularFile(output));

    Document document = builder.build(output.toFile());
    Nodes links = document.query("//a/@href");
    Assert.assertEquals(3, links.size());
    Node snapshotLink = links.get(2);
    // 2.1.0-SNAPSHOT has directory 'snapshot'
    Assert.assertEquals("snapshot/index.html", snapshotLink.getValue());
  }
}
