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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Test;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.ValidityException;
import nu.xom.XPathContext;

public class TestPomTest {
  
  private XPathContext context = new XPathContext();
  private Document pom;
  
  @Before 
  public void setUp() throws IOException, ParsingException {
    List<Artifact> dependencies = new ArrayList<>();
    Artifact artifact = new DefaultArtifact("com.google.guava:guava:30.1.1-android");
    Path created = TestPom.create(artifact, dependencies);    
    Builder builder = new Builder();
    pom = builder.build(created.toFile());
    
    context.addNamespace("pom", "http://maven.apache.org/POM/4.0.0");
    
  }

  @Test
  public void testMainDependency() {
    Nodes dependencyElements = pom.query("/pom:project/pom:dependencies/pom:dependency", context);
    assertEquals(1, dependencyElements.size());
    Element element = (Element) dependencyElements.get(0);
    String groupId =
        element.getFirstChildElement("groupId", "http://maven.apache.org/POM/4.0.0").getValue();
    String artifactId =
        element.getFirstChildElement("artifactId", "http://maven.apache.org/POM/4.0.0").getValue();
    String version =
        element.getFirstChildElement("version", "http://maven.apache.org/POM/4.0.0").getValue();

    assertEquals("com.google.guava", groupId);
    assertEquals("guava", artifactId);
    assertEquals("30.1.1-android", version);
  }
  
  @Test
  public void testModelVersion() {
    Nodes modelVersion = pom.query("/pom:project/pom:modelVersion", context);
    assertEquals("4.0.0", modelVersion.get(0).getValue());
  }
  
}
