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

import static org.junit.Assert.assertEquals;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Path;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.ParsingException;
import nu.xom.XPathContext;
import org.junit.Test;

public class MavenProjectModifierTest {
  private XPathContext context =
      new XPathContext("ns", MavenProjectModifier.MAVEN_POM_NAMESPACE_URL);

  @Test
  public void testSurefirePluginConfiguration() throws Exception {
    BuildFileModifier modifier = Modification.MAVEN.getModifier();
    Bom bom = Bom.readBom("com.google.guava:guava-bom:30.0-jre");

    Path copiedProject = TestHelper.createProjectCopy("testdata/example-maven-project");
    modifier.modifyFiles("test", copiedProject, bom);

    Path rootPomFile = copiedProject.resolve("pom.xml");

    Builder builder = new Builder();
    Document document = builder.build(rootPomFile.toFile());
    verifyGuavaBomModification(document);

    Path subProjectPomFile = copiedProject.resolve("subproject").resolve("pom.xml");
    Document subProjectDocument = builder.build(subProjectPomFile.toFile());

    verifyGuavaBomModification(subProjectDocument);
  }

  void verifyTestClassifierModification(Document document) throws ParsingException, IOException {
    ImmutableList<Node> dependencyNodes =
        ImmutableList.copyOf(document.query("//ns:project/ns:build/ns:dependencies/ns:dependency", context));

    Element firstDependency = (Element) dependencyNodes.get(0);

    firstDependency.getChildElements("version");
  }

  void verifyGuavaBomModification(Document document) throws ParsingException, IOException {
    ImmutableList<Node> pluginElements =
        ImmutableList.copyOf(document.query("//ns:project/ns:build/ns:plugins/ns:plugin", context));

    Truth.assertThat(pluginElements).hasSize(1);
    Node surefirePluginNode = pluginElements.get(0);
    assertEquals(
        "maven-surefire-plugin",
        surefirePluginNode.query("ns:artifactId", context).get(0).getValue());
    assertEquals(
        "org.apache.maven.plugins",
        surefirePluginNode.query("ns:groupId", context).get(0).getValue());

    ImmutableList<Node> additionalClasspathElements =
        ImmutableList.copyOf(
            surefirePluginNode.query(
                "ns:configuration/ns:additionalClasspathElements/ns:additionalClasspathElement",
                context));

    // The artifacts in the guava-bom 30.0 and their dependencies.
    Truth.assertThat(additionalClasspathElements).hasSize(69);

    ImmutableList<Node> classpathDependencyExcludes =
        ImmutableList.copyOf(
            surefirePluginNode.query(
                "ns:configuration/ns:classpathDependencyExcludes/ns:classpathDependencyExclude",
                context));
    Truth.assertThat(classpathDependencyExcludes).hasSize(69);
  }
}
