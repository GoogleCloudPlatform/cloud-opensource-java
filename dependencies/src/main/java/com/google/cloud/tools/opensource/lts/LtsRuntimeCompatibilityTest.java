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

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.FileWriteMode;
import com.google.common.io.MoreFiles;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.XPathContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.yaml.snakeyaml.Yaml;

/**
 * Runs test for each repository of the libraries in the LTS BOM
 *
 * src/resources/repositories.yaml
 */
public class LtsRuntimeCompatibilityTest {

  public static void main(String[] arguments)
      throws IOException, ArtifactDescriptorException, InterruptedException, ParsingException {
    String inputFileName = arguments[0];

    Yaml yaml = new Yaml();



    Path inputFile = Paths.get(inputFileName);
    Map<String, Object> input = yaml.load(new FileInputStream(inputFile.toFile()));

    String bomCoordinates = (String) input.get("bom");
    Bom bom = Bom.readBom(bomCoordinates);

    List<Map<String, Object>> repositories = (List<Map<String, Object>>) input.get("repositories");

    Path testRoot = Files.createTempDirectory("lts-test");
    System.out.println("Root directory:" + testRoot);
    // testRoot.toFile().deleteOnExit();

    int i = 0;
    for (Map<String, Object> repository: repositories) {
      String name = checkNotNull((String) repository.get("name"));
      URL url = new URL(checkNotNull((String) repository.get("url")));
      String gitTag = checkNotNull((String) repository.get("tag"));
      String commands = checkNotNull((String) repository.get("commands"));
      // /grpc/grpc-java
      String urlPath =  url.getPath();
      // grpc-java.git
      String secondPathElement = urlPath.split("/")[2];
      String projectDirectoryName = secondPathElement.replace(".git", "");
      Path projectDirectory = testRoot.resolve(projectDirectoryName);

      System.out.println(name + ": " + url + " at " + gitTag);

      Process gitClone = Runtime.getRuntime()
          .exec(String.format("git clone -b %s --depth=1 %s", gitTag, url),
              null, testRoot.toFile());

      int checkoutStatusCode = gitClone.waitFor();

      com.google.common.io.Files.asCharSink(projectDirectory.resolve("stdout.log").toFile(),
          Charsets.UTF_8, FileWriteMode.APPEND).writeFrom(new InputStreamReader(gitClone.getInputStream()));
      com.google.common.io.Files.asCharSink(projectDirectory.resolve("stderr.log").toFile(),
          Charsets.UTF_8, FileWriteMode.APPEND).writeFrom(new InputStreamReader(gitClone.getErrorStream()));

      if (checkoutStatusCode != 0) {
        System.out.println("Failed to checkout " +
            url +". Exiting. Check the logs in " + projectDirectory);
        break;
      }

      // Modify build file to use the BOM
      modifyPomFiles(testRoot, bom);

      // Build the project

      Path shellScript = testRoot.resolve("test_" + i + ".sh");
      String shellScriptLocation = shellScript.toAbsolutePath().toString();
      com.google.common.io.Files.asCharSink(shellScript.toFile(), Charsets.UTF_8).write(commands);

      Process buildProcess = Runtime.getRuntime()
          .exec(String.format("/bin/bash %s", shellScriptLocation),
              null, projectDirectory.toFile());

      com.google.common.io.Files.asCharSink(projectDirectory.resolve("stdout.log").toFile(),
          Charsets.UTF_8, FileWriteMode.APPEND).writeFrom(new InputStreamReader(buildProcess.getInputStream()));
      com.google.common.io.Files.asCharSink(projectDirectory.resolve("stderr.log").toFile(),
          Charsets.UTF_8, FileWriteMode.APPEND).writeFrom(new InputStreamReader(buildProcess.getErrorStream()));

      int buildStatusCode = buildProcess.waitFor();

      if (buildStatusCode != 0) {
        System.out.println("Failed to build " + url +". Exiting. Check the logs in " + projectDirectory);
        break;
      } else {
        System.out.println(name + " passed!");
      }
    }
  }

  static void modifyPomFiles(Path projectRoot, Bom bom) throws IOException, ParsingException {
    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(projectRoot);

    for (Path path : paths) {
      if (!path.getFileName().endsWith("pom.xml")) {
        continue;
      }

      modifyPomFile(path, bom);

    }
  }

  static ImmutableMap<String, String> versionlessCoordinatesToVersion(Bom bom) {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();

    for (Artifact managedDependency : bom.getManagedDependencies()) {
      map.put(managedDependency.getGroupId() + ":" + managedDependency.getArtifactId(),
          managedDependency.getVersion());
    }

    return map.build();
  }

  static void modifyPomFile(Path pomFile, Bom bom)
      throws IOException, ParsingException {
    Builder builder = new Builder();
    XPathContext context = new XPathContext("ns", "http://maven.apache.org/POM/4.0.0");
    Document document = builder.build(pomFile.toFile());
    Nodes project = document.query("//ns:project", context);
    checkArgument(project.size() == 1);

    ImmutableMap<String, String> bomMembers = versionlessCoordinatesToVersion(bom);

    Nodes dependencyNodes = document.query("//ns:project/ns:dependencies/ns:dependency", context);
    for (Node dependencyNode : dependencyNodes) {
      String groupId = dependencyNode.query("ns:groupId", context).get(0).getValue();
      String artifactId = dependencyNode.query("ns:artifactId", context).get(0).getValue();
      Nodes versionNode = dependencyNode.query("ns:version",context);

      String versionlessCoordinates = groupId + ":" + artifactId;

      String versionFromBom = bomMembers.get(versionlessCoordinates);
      if (versionFromBom != null) {
        if (versionNode.size() == 0) {
          // Add the version
          Element version = new Element("version");
          version.appendChild(versionFromBom);
          ((Element)dependencyNode).appendChild(version);
          System.out.println("Added version element to " + versionlessCoordinates + " in " + pomFile);
        } else {
          // Replace the version
          Element version = (Element) versionNode.get(0);
          version.removeChildren();
          version.appendChild(versionFromBom);
          System.out.println("Inserted version element to " + versionlessCoordinates + " in " + pomFile);
        }
      }
    }

    com.google.common.io.Files.asCharSink(pomFile.toFile(),
        Charsets.UTF_8).write(document.toXML());

    System.out.println("Write "+pomFile);
  }

}
