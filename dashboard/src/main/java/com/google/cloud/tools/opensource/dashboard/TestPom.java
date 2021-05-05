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

package com.google.cloud.tools.opensource.dashboard;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Serializer;

public class TestPom {

  private static final String MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

  static Path create(Artifact artifact, List<Artifact> dependencies) throws IOException {
    Path temp = Files.createTempDirectory("pom");
    Path local = temp.resolve("pom.xml");
    Element project = new Element("project", MAVEN_NAMESPACE);
    Document pom = new Document(project);
    
    appendChildElement(project, "modelVersion", "4.0.0");
    appendChildElement(project, "groupId", "com.google.cloud.tools");
    appendChildElement(project, "artifactId", artifact.getArtifactId() + "-ltstest");
    appendChildElement(project, "version", "1.0.0");
    
    
    Element dependenciesElement = new Element("dependencies", MAVEN_NAMESPACE);
    
    // dependency element for the artifact under test
    Element mainDependencyElement = makeDependencyElement(artifact);
    
    // testlib dependency element for the artifact under test
    Element testDependencyElement = makeDependencyElement(artifact);
    appendChildElement(testDependencyElement, "classifier", "tests");
    appendChildElement(testDependencyElement, "scope", "test");
    appendChildElement(testDependencyElement, "type", "test-jar");
    
    dependenciesElement.appendChild(mainDependencyElement);
    dependenciesElement.appendChild(testDependencyElement);
    project.appendChild(dependenciesElement);
    
    /*
    
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.0.0-M5</version>
        <configuration>
          <dependenciesToScan>
            <dependency>com.google.cloud:google-cloud-storage:test-jar:tests:*</dependency>
          </dependenciesToScan>
        </configuration>
      </plugin>
    </plugins>
  </build>
     */
    
    Element buildElement = new Element("build", MAVEN_NAMESPACE);
    Element pluginsElement = new Element("plugins", MAVEN_NAMESPACE);
    Element pluginElement = new Element("plugin", MAVEN_NAMESPACE);
    appendChildElement(pluginElement, "groupId", "org.apache.maven.plugins");
    appendChildElement(pluginElement, "artifactId", "maven-surefire-plugin");
    appendChildElement(pluginElement, "version", "3.0.0-M5");
    Element configurationElement = new Element("configuration", MAVEN_NAMESPACE);
    Element dependenciesToScan = new Element("dependenciesToScan", MAVEN_NAMESPACE);
    String dependencyToScan = Artifacts.makeKey(artifact) + ":test-jar:tests:*";
    appendChildElement(dependenciesToScan, "dependency", dependencyToScan);
    configurationElement.appendChild(dependenciesToScan);
    pluginElement.appendChild(configurationElement);
    pluginsElement.appendChild(pluginElement);
    buildElement.appendChild(pluginsElement);
    project.appendChild(buildElement);

    try (OutputStream out = Files.newOutputStream(local)) {
      Serializer serializer = new Serializer(out);
      serializer.write(pom);
      serializer.flush();
    }
    
    return local;    
  }

  private static Element makeDependencyElement(Artifact artifact) {
    Element dependencyElement = new Element("dependency", MAVEN_NAMESPACE);
    Element groupId = new Element("groupId", MAVEN_NAMESPACE);
    groupId.appendChild(artifact.getGroupId());
    Element artifactId = new Element("artifactId", MAVEN_NAMESPACE);
    Element version = new Element("version", MAVEN_NAMESPACE);
    artifactId.appendChild(artifact.getArtifactId());
    version.appendChild(artifact.getVersion());
    dependencyElement.appendChild(groupId);
    dependencyElement.appendChild(artifactId);
    dependencyElement.appendChild(version);
    return dependencyElement;
  }

  private static void appendChildElement(Element parent, String name, String value) {
    Element modelVersion = new Element(name, MAVEN_NAMESPACE);
    modelVersion.appendChild(value);
    parent.appendChild(modelVersion);
  }

}
