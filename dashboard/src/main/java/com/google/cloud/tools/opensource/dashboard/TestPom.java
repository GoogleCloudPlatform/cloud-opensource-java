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
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Serializer;

public class TestPom {

  private static final String MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

  static Path create(Artifact artifact, List<Artifact> dependencies) throws IOException {
    Path temp = Files.createTempDirectory("pom");
    Path local = temp.resolve("pom.xml");
    Element root = new Element("project", MAVEN_NAMESPACE);
    Document pom = new Document(root);
    
    appendChildElement(root, "modelVersion", "4.0.0");
    appendChildElement(root, "groupId", "com.google.cloud.tools");
    appendChildElement(root, "artifactId", artifact.getArtifactId() + "-ltstest");
    appendChildElement(root, "version", "1.0.0");
    
    Element dependenciesElement = new Element("dependencies", MAVEN_NAMESPACE);
    Element dependencyElement = new Element("dependency", MAVEN_NAMESPACE);
    Element groupId = new Element("groupId", MAVEN_NAMESPACE);
    groupId.appendChild(artifact.getGroupId());
    Element artifactId = new Element("artifactId", MAVEN_NAMESPACE);
    Element version = new Element("version", MAVEN_NAMESPACE);
    artifactId.appendChild(artifact.getArtifactId());
    version.appendChild(artifact.getVersion());
    
    root.appendChild(dependenciesElement);
    
    dependenciesElement.appendChild(dependencyElement);
    dependencyElement.appendChild(groupId);
    dependencyElement.appendChild(artifactId);
    dependencyElement.appendChild(version);

    try (OutputStream out = Files.newOutputStream(local)) {
      Serializer serializer = new Serializer(out);
      serializer.write(pom);
      serializer.flush();
    }
    
    return local;    
  }

  private static void appendChildElement(Element root, String name, String value) {
    Element modelVersion = new Element(name, MAVEN_NAMESPACE);
    modelVersion.appendChild(value);
    root.appendChild(modelVersion);
  }

}
