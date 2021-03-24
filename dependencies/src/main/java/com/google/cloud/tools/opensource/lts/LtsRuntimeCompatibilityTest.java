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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.FileWriteMode;
import com.google.common.io.MoreFiles;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParentNode;
import nu.xom.ParsingException;
import nu.xom.XPathContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Runs test for each repository of the libraries in the LTS BOM
 *
 * <p>src/resources/repositories.yaml
 */
public class LtsRuntimeCompatibilityTest {

  public static void main(String[] arguments)
      throws IOException, ArtifactDescriptorException, InterruptedException, ParsingException {
    String inputFileName = arguments[0];

    // SafeConstructor to parse YAML only with simple values
    Yaml yaml = new Yaml(new SafeConstructor());

    Path inputFile = Paths.get(inputFileName);
    Map<String, Object> input = yaml.load(new FileInputStream(inputFile.toFile()));

    String bomCoordinates = (String) input.get("bom");
    Bom bom = Bom.readBom(bomCoordinates);

    List<Map<String, Object>> repositories = (List<Map<String, Object>>) input.get("repositories");

    Path testRoot = Files.createTempDirectory("lts-test");
    System.out.println("Root directory: " + testRoot);
    // testRoot.toFile().deleteOnExit();

    int i = 0;
    for (Map<String, Object> repository : repositories) {
      String name = checkNotNull((String) repository.get("name"));
      URL url = new URL(checkNotNull((String) repository.get("url")));
      String gitTag = checkNotNull((String) repository.get("tag"));

      String modification = checkNotNull((String) repository.get("modification"));
      String commands = checkNotNull((String) repository.get("commands"));
      // /grpc/grpc-java
      String urlPath = url.getPath();
      // grpc-java.git
      String secondPathElement = urlPath.split("/")[2];
      String projectDirectoryName = secondPathElement.replace(".git", "");
      Path projectDirectory = testRoot.resolve(projectDirectoryName);

      System.out.println(name + ": " + url + " at " + gitTag);

      Process gitClone =
          Runtime.getRuntime()
              .exec(
                  String.format("git clone -b %s --depth=1 %s", gitTag, url),
                  null,
                  testRoot.toFile());

      int checkoutStatusCode = gitClone.waitFor();

      com.google.common.io.Files.asCharSink(
              projectDirectory.resolve("stdout.log").toFile(), Charsets.UTF_8, FileWriteMode.APPEND)
          .writeFrom(new InputStreamReader(gitClone.getInputStream()));
      com.google.common.io.Files.asCharSink(
              projectDirectory.resolve("stderr.log").toFile(), Charsets.UTF_8, FileWriteMode.APPEND)
          .writeFrom(new InputStreamReader(gitClone.getErrorStream()));

      if (checkoutStatusCode != 0) {
        System.out.println(
            "Failed to checkout " + url + ". Exiting. Check the logs in " + projectDirectory);
        break;
      }

      // Modify build file to use the BOM
      if ("Maven".equals(modification)) {
        modifyPomFiles(projectDirectory, bom);
      } else if ("Gradle".equals(modification)) {
        modifyGradleFiles(projectDirectory, bom);
      } else {
        System.out.println("Invalid value for modification. 'Maven' or 'Gradle'");
        break;
      }

      // Build the project

      Path shellScript = testRoot.resolve("test_" + i++ + ".sh");
      String shellScriptLocation = shellScript.toAbsolutePath().toString();
      com.google.common.io.Files.asCharSink(shellScript.toFile(), Charsets.UTF_8).write(commands);

      Process buildProcess =
          Runtime.getRuntime()
              .exec(
                  String.format("/bin/bash %s", shellScriptLocation),
                  null,
                  projectDirectory.toFile());

      com.google.common.io.Files.asCharSink(
              projectDirectory.resolve("stdout.log").toFile(), Charsets.UTF_8, FileWriteMode.APPEND)
          .writeFrom(new InputStreamReader(buildProcess.getInputStream()));
      com.google.common.io.Files.asCharSink(
              projectDirectory.resolve("stderr.log").toFile(), Charsets.UTF_8, FileWriteMode.APPEND)
          .writeFrom(new InputStreamReader(buildProcess.getErrorStream()));

      int buildStatusCode = buildProcess.waitFor();

      if (buildStatusCode != 0) {
        System.out.println(
            "Failed to build " + url + ". Exiting. Check the logs in " + projectDirectory);
        break;
      } else {
        System.out.println(name + " passed!");
      }
    }
  }

  static void modifyGradleFiles(Path projectRoot, Bom bom) throws IOException {
    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(projectRoot);

    for (Path path : paths) {
      if (!path.getFileName().endsWith("build.gradle")) {
        continue;
      }

      modifyGradleFile(path, bom);
    }
  }

  static void modifyGradleFile(Path gradleFile, Bom bom) throws IOException {

    List<String> lines = com.google.common.io.Files.readLines(gradleFile.toFile(), Charsets.UTF_8);

    String bomCoordinates = bom.getCoordinates();

    List<String> replacedLines =
        lines.stream()
            .map(
                line ->
                    line.replaceAll(
                        "^dependencies \\{",
                        "dependencies {\n    testRuntime enforcedPlatform('"
                            + bomCoordinates
                            + "')"))
            .collect(Collectors.toList());

    com.google.common.io.Files.asCharSink(gradleFile.toFile(), Charsets.UTF_8)
        .write(Joiner.on("\n").join(replacedLines));
  }

  static void modifyPomFiles(Path projectRoot, Bom bom) throws IOException, ParsingException {
    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(projectRoot);

    ImmutableList<Artifact> bomManagedDependencies = bom.getManagedDependencies();
    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult resolvedDependencies = classPathBuilder.resolve(bomManagedDependencies, false);
    ImmutableList<ClassPathEntry> resolvedManagedDependencies =
        resolvedDependencies.getClassPath().subList(0, bomManagedDependencies.size());

    for (Path path : paths) {
      if (!path.getFileName().endsWith("pom.xml")) {
        continue;
      }

      modifyPomFile(path, resolvedManagedDependencies);
    }
  }

  static String mavenPomNamespaceUri = "http://maven.apache.org/POM/4.0.0";

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
   */
  static void modifyPomFile(Path pomFile, ImmutableList<ClassPathEntry> managedDependencies)
      throws IOException, ParsingException {
    Builder builder = new Builder();
    XPathContext context = new XPathContext("ns", mavenPomNamespaceUri);
    Document document = builder.build(pomFile.toFile());
    Nodes project = document.query("//ns:project", context);
    if (project.size() != 1) {
      System.err.println("Invalid pom.xml " + pomFile+"; project element size: "+project.size());
      return;
    }

    // Look at project/build/plugins/plugin for surefire plugin
    Nodes classifierNodes = document.query("//ns:project/ns:dependencies/ns:dependency/ns:classifier", context);

    Set<String> dependenciesWithClassifiers = new HashSet<>();
    for (Node classifierNode : classifierNodes) {
      String classifierValue = classifierNode.getValue();
      if (classifierValue.isEmpty()) {
        continue;
      }
      Element dependency = (Element) classifierNode.getParent();
      // A dependency element always has an artifactId and a groupId element.
      String artifactId = dependency.getChildElements("artifactId", mavenPomNamespaceUri).get(0).getValue();
      String groupId = dependency.getChildElements("groupId", mavenPomNamespaceUri).get(0).getValue();
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

    for (ClassPathEntry bomManagedDependency : managedDependencies) {
      File file = bomManagedDependency.getArtifact().getFile();
      Element additionalClasspathElement =
          new Element("additionalClasspathElement", mavenPomNamespaceUri);
      additionalClasspathElement.appendChild(file.getAbsolutePath());
      additionalClasspathElements.appendChild(additionalClasspathElement);
    }

    // This unexpectedly removes dependencies even if they use classifiers. For example,
    // com.google.api.gax.grpc.testing.MockServiceHelper is in gax-grpc with testlib classifier
    // the BOM does not supply the testlib-classifier artifacts.
    Element classpathDependencyExcludes =
        getOrCreateNode(surefireConfigurationElement, "classpathDependencyExcludes");
    for (ClassPathEntry bomManagedDependency : managedDependencies) {
      Artifact artifact = bomManagedDependency.getArtifact();
      String versionlessCoordinates = Artifacts.makeKey(artifact);
      if (dependenciesWithClassifiers.contains(versionlessCoordinates)) {
        // Because surefire configuration cannot handle classifiers (such as
        // com.google.gax:gax-grpc:testlib), we cannot replace them
        continue;
      }
      Element classpathDependencyExclude =
          new Element("classpathDependencyExclude", mavenPomNamespaceUri);
      classpathDependencyExclude.appendChild(versionlessCoordinates);
      classpathDependencyExcludes.appendChild(classpathDependencyExclude);
    }

    com.google.common.io.Files.asCharSink(pomFile.toFile(), Charsets.UTF_8).write(document.toXML());
  }
}
