/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.opensource.lts;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.GradleDependencyMediation;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.Text;
import nu.xom.XPathContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.version.InvalidVersionSpecificationException;

/**
 * Runs the test specified in the {@code testCase} with the presence of the libraries in the LTS
 * BOM.
 */
class LtsCompatibilityTestRunner {
  private static final Logger logger = Logger.getLogger(LtsCompatibilityTestRunner.class.getName());

  private final RepositoryTestCase testCase;

  LtsCompatibilityTestRunner(RepositoryTestCase testCase) {
    this.testCase = testCase;
  }

  void run(Bom bom, Path testRoot, Path runnerLog)
      throws IOException, InterruptedException, ParsingException, TestFailureException,
          InvalidVersionSpecificationException {
    String name = testCase.getName();
    String commands = testCase.getCommands();

    URL url = testCase.getGitUrl();
    // Example: "/grpc/grpc-java.git"
    String urlPath = url.getPath();
    // Example: "grpc-java.git"
    String secondPathElement = urlPath.split("/")[2];
    String projectDirectoryName = secondPathElement.replace(".git", "");
    Path projectDirectory = testRoot.resolve(projectDirectoryName);

    String gitTag = testCase.getGitTag();
    logger.info(name + ": " + url + " at " + gitTag);

    File gitOutput = testRoot.resolve("lts_test_git.log").toFile();
    Process gitProcess =
        new ProcessBuilder("git", "clone", "-b", gitTag, "--depth=1", url.toString())
            .directory(testRoot.toFile())
            .redirectErrorStream(true)
            .redirectOutput(gitOutput)
            .start();

    int checkoutStatusCode = gitProcess.waitFor();

    if (checkoutStatusCode != 0) {
      String outputContent =
          com.google.common.io.Files.asCharSource(gitOutput, Charsets.UTF_8).read();
      logger.severe("Failed to checkout the repository:\n" + outputContent);
      throw new TestFailureException("Could not checkout the Git URL: " + url);
    }
    logger.info("Successfully checked out the repository at " + projectDirectory);

    Modification modification = testCase.getModification();
    // Modify build file to use the BOM
    if (modification == Modification.MAVEN) {
      modifyPomFiles(projectDirectory, bom);
    } else if (modification == Modification.GRADLE) {
      modifyGradleFiles(name, projectDirectory, bom);
    } else if (modification == Modification.SKIP) {
      logger.info("No modification to the build files");
    } else {
      throw new IllegalArgumentException(
          "Invalid value for modification field. It must be 'Maven' or 'Gradle'");
    }

    // Build the project
    Path shellScript = projectDirectory.resolve("lts_test.sh");
    String shellScriptLocation = shellScript.toAbsolutePath().toString();
    com.google.common.io.Files.asCharSink(shellScript.toFile(), Charsets.UTF_8).write(commands);

    logger.info("Running the commands");

    File output = projectDirectory.resolve("lts_test.log").toFile();

    // "-e" to fail on errors
    Process bashProcess =
        new ProcessBuilder("/bin/bash", "-e", shellScriptLocation)
            .directory(projectDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output)
            .start();

    int buildStatusCode = bashProcess.waitFor();

    if (buildStatusCode != 0) {
      String outputContent = com.google.common.io.Files.asCharSource(output, Charsets.UTF_8).read();
      logger.severe("Output:\n" + outputContent);

      // Avoid messing up the log with the output and the exception
      Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(1));
      throw new TestFailureException("Failed to run the commands.");
    } else {
      logger.info(name + " passed.");
    }
  }

  static void modifyPomFiles(Path projectRoot, Bom bom)
      throws IOException, ParsingException, InvalidVersionSpecificationException {
    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(projectRoot);

    ImmutableList<Artifact> bomManagedDependencies = bom.getManagedDependencies();
    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult resolvedDependencies =
        classPathBuilder.resolve(
            bomManagedDependencies, false, GradleDependencyMediation.withEnforcedPlatform(bom));

    // Include the BOM members' dependencies as well; otherwise we may get NoClassDefFoundEerror for
    // artifacts that declare new dependencies in newer versions.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/pull/1982#issuecomment-812201558
    ImmutableList<ClassPathEntry> resolvedManagedDependencies = resolvedDependencies.getClassPath();

    StringBuilder bomDependencyMessage = new StringBuilder("Dependencies from BOM:\n");
    for (ClassPathEntry resolvedManagedDependency : resolvedManagedDependencies) {
      bomDependencyMessage.append("  ");
      bomDependencyMessage.append(resolvedManagedDependency.getArtifact());
      bomDependencyMessage.append("\n");
    }
    logger.info(bomDependencyMessage.toString());

    for (Path path : paths) {
      if (!path.getFileName().endsWith("pom.xml")) {
        continue;
      }

      modifyPomFile(path, resolvedManagedDependencies);
    }
  }

  static final String mavenPomNamespaceUri = "http://maven.apache.org/POM/4.0.0";

  static Element getOrCreateNode(Element parent, String name) {
    Elements targetNodes = parent.getChildElements(name, mavenPomNamespaceUri);
    if (targetNodes.size() >= 1) {
      return targetNodes.get(0);
    }
    Element newNode = new Element(name, mavenPomNamespaceUri);
    parent.appendChild(newNode);

    return newNode;
  }

  static Element getOrCreateSurefirePlugin(Element pluginsNode) {
    Elements pluginElements = pluginsNode.getChildElements();
    for (Element pluginElement : pluginElements) {
      Elements artifactIdElements =
          pluginElement.getChildElements("artifactId", mavenPomNamespaceUri);
      if (artifactIdElements.size() == 1
          && "maven-surefire-plugin".equals(artifactIdElements.get(0).getValue())) {
        return pluginElement;
      }
    }

    Element newPluginNode = new Element("plugin", mavenPomNamespaceUri);
    Element artifactIdElement = new Element("artifactId", mavenPomNamespaceUri);
    artifactIdElement.appendChild("maven-surefire-plugin");
    Element groupIdElement = new Element("groupId", mavenPomNamespaceUri);
    groupIdElement.appendChild("org.apache.maven.plugins");
    newPluginNode.appendChild(groupIdElement);
    newPluginNode.appendChild(artifactIdElement);

    pluginsNode.appendChild(newPluginNode);
    return newPluginNode;
  }

  /**
   * Modifies {@code pomFile} so that Maven uses {@code managedDependencies} when running the tests.
   *
   * <p>It inserts new line characters so that the items in the list is easily visible in a vertical
   * scroll. This does not try to
   */
  static void modifyPomFile(Path pomFile, ImmutableList<ClassPathEntry> managedDependencies)
      throws IOException, ParsingException {
    Builder builder = new Builder();
    XPathContext context = new XPathContext("ns", mavenPomNamespaceUri);
    Document document = builder.build(pomFile.toFile());
    Nodes project = document.query("//ns:project", context);
    if (project.size() != 1) {
      // When sample's XML declaration is missing namespace, this logic fails.
      // Example: https://github.com/googleapis/java-bigquery/pull/1199
      logger.warning("Invalid pom.xml " + pomFile + "; project element size: " + project.size());
      return;
    }

    // Look at project/build/plugins/plugin for surefire plugin
    Nodes classifierNodes =
        document.query("//ns:project/ns:dependencies/ns:dependency/ns:classifier", context);

    Set<String> dependenciesWithClassifiers = new HashSet<>();
    for (Node classifierNode : classifierNodes) {
      String classifierValue = classifierNode.getValue();
      if (classifierValue.isEmpty()) {
        continue;
      }
      Element dependency = (Element) classifierNode.getParent();
      // A dependency element always has an artifactId and a groupId element.
      String artifactId =
          dependency.getChildElements("artifactId", mavenPomNamespaceUri).get(0).getValue();
      String groupId =
          dependency.getChildElements("groupId", mavenPomNamespaceUri).get(0).getValue();
      dependenciesWithClassifiers.add(groupId + ":" + artifactId);
    }

    Element projectNode = (Element) document.query("//ns:project", context).get(0);
    Element buildNode = getOrCreateNode(projectNode, "build");
    Element pluginsNode = getOrCreateNode(buildNode, "plugins");
    Element surefirePluginElement = getOrCreateSurefirePlugin(pluginsNode);

    // https://maven.apache.org/surefire/maven-surefire-plugin/examples/configuring-classpath.html

    Element surefireConfigurationElement = getOrCreateNode(surefirePluginElement, "configuration");
    Element additionalClasspathElements =
        getOrCreateNode(surefireConfigurationElement, "additionalClasspathElements");
    additionalClasspathElements.appendChild(new Text("\n"));
    for (ClassPathEntry bomManagedDependency : managedDependencies) {
      File file = bomManagedDependency.getArtifact().getFile();
      Element additionalClasspathElement =
          new Element("additionalClasspathElement", mavenPomNamespaceUri);
      additionalClasspathElement.appendChild(file.getAbsolutePath());
      additionalClasspathElements.appendChild(additionalClasspathElement);
      additionalClasspathElements.appendChild(new Text("\n"));
    }

    // This unexpectedly removes dependencies even if they use classifiers. For example,
    // com.google.api.gax.grpc.testing.MockServiceHelper is in gax-grpc with testlib classifier
    // the BOM does not supply the testlib-classifier artifacts.
    Element classpathDependencyExcludes =
        getOrCreateNode(surefireConfigurationElement, "classpathDependencyExcludes");
    classpathDependencyExcludes.appendChild(new Text("\n"));
    for (ClassPathEntry bomManagedDependency : managedDependencies) {
      Artifact artifact = bomManagedDependency.getArtifact();
      String versionlessCoordinates = Artifacts.makeKey(artifact);
      if (dependenciesWithClassifiers.contains(versionlessCoordinates)) {
        // Because surefire configuration cannot handle artifacts with classifiers (such as
        // com.google.gax:gax-grpc:testlib), we cannot exclude them.
        continue;
      }
      Element classpathDependencyExclude =
          new Element("classpathDependencyExclude", mavenPomNamespaceUri);
      classpathDependencyExclude.appendChild(versionlessCoordinates);
      classpathDependencyExcludes.appendChild(classpathDependencyExclude);
      classpathDependencyExcludes.appendChild(new Text("\n"));
    }

    com.google.common.io.Files.asCharSink(pomFile.toFile(), Charsets.UTF_8).write(document.toXML());
  }

  static void modifyGradleFiles(String name, Path projectRoot, Bom bom) throws IOException {
    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(projectRoot);

    for (Path path : paths) {
      if (!path.getFileName().endsWith("build.gradle")) {
        continue;
      }

      if ("beam".equals(name)) {
        modifyBeamGradleFile(path, bom);
      } else {
        modifyGradleFile(path, bom);
      }
    }
  }

  static void modifyGradleFile(Path gradleFile, Bom bom) throws IOException {
    String buildGradleContent =
        Files.asCharSource(gradleFile.toFile(), StandardCharsets.UTF_8).read();

    String bomCoordinates = bom.getCoordinates();

    buildGradleContent =
        buildGradleContent.replaceAll(
            "\ndependencies \\{",
            "\ndependencies {\n    testRuntime enforcedPlatform('" + bomCoordinates + "')");

    com.google.common.io.Files.asCharSink(gradleFile.toFile(), Charsets.UTF_8)
        .write(buildGradleContent);
  }

  // build.gradle files that run as part of the test in the beam seciton of repositories.yaml
  static final ImmutableList<Path> beamTestSubprojects =
      ImmutableList.of(
          Paths.get("sdks", "java", "core", "build.gradle"),
          Paths.get("sdks", "java", "io", "google-cloud-platform", "build.gradle"),
          Paths.get("sdks", "java", "extensions", "google-cloud-platform-core", "build.gradle"),
          Paths.get("runners", "google-cloud-dataflow-java", "build.gradle"));

  static void modifyBeamGradleFile(Path gradleFile, Bom bom) throws IOException {

    // Beam already uses enforcedPlatform(google_cloud_platform_libraries_bom), which prevents
    // gcp-lts-bom's setting gRPC library version.
    if (beamTestSubprojects.stream().anyMatch(gradleFile::endsWith)) {
      String buildGradleContent =
          Files.asCharSource(gradleFile.toFile(), StandardCharsets.UTF_8).read();

      String bomCoordinates = bom.getCoordinates();
      buildGradleContent =
          buildGradleContent.replaceAll(
              "\ndependencies \\{",
              "\ndependencies {\n    compile enforcedPlatform('"
                  + bomCoordinates
                  + "')\n"
                  + "    testRuntime enforcedPlatform('"
                  + bomCoordinates
                  + "')");

      // Tried compileOnly but analyzeTestClassesDependencies's configuratin cannot resolve
      // the dependencies.
      // https://github.com/GoogleCloudPlatform/cloud-opensource-java/pull/1982#discussion_r610878573
      // buildGradleContent =
      //    buildGradleContent.replaceAll(
      //      "compile enforcedPlatform\\(library.java.google_cloud_platform_libraries_bom\\)",
      //      "compileOnly enforcedPlatform(library.java.google_cloud_platform_libraries_bom)");
      com.google.common.io.Files.asCharSink(gradleFile.toFile(), Charsets.UTF_8)
          .write(buildGradleContent);
    }
  }
}
