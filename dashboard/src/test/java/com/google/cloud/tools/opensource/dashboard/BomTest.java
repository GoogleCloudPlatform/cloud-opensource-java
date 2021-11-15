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

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.DependencyMediation;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.junit.Assert;
import org.junit.Test;

public class BomTest {

  @Test
  public void testLtsBom() throws Exception {
    Path bomPath = Paths.get("..", "boms", "cloud-lts-bom", "pom.xml").toAbsolutePath();  
    checkBom(bomPath);
  }

  @Test
  public void testLibrariesBom() throws Exception {
    Path bomPath = Paths.get("..", "boms", "cloud-oss-bom", "pom.xml").toAbsolutePath();  
    checkBom(bomPath);
  }

  private void checkBom(Path bomPath) throws Exception {
    Bom bom = Bom.readBom(bomPath);
    List<Artifact> artifacts = bom.getManagedDependencies();
    for (Artifact artifact : artifacts) {
      assertReachable(buildMavenCentralUrl(artifact));
    }

    assertNoDowngradeRule(bom);
  }

  private static String buildMavenCentralUrl(Artifact artifact) {
    return "https://repo1.maven.org/maven2/"
        + artifact.getGroupId().replace('.', '/')
        + "/"
        + artifact.getArtifactId()
        + "/"
        + artifact.getVersion()
        + "/";
  }

  private static void assertReachable(String url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setRequestMethod("HEAD");
    try {
      Assert.assertEquals(
          "Could not reach " + url, HttpURLConnection.HTTP_OK, connection.getResponseCode());
    } catch (IOException ex) {
      Assert.fail("Could not reach " + url + "\n" + ex.getMessage());
    }
  }

  /**
   * Asserts that the members of the {@code bom} satisfy the no-downgrade rule. This rule asserts
   * that the members have the highest versions among the dependencies of them. For example, if
   * google-http-client 1.40.1 is in the BOM, then no other libraries in the BOM must not depend on
   * the higher version of the google-http-client.
   *
   * @param bom the BOM to assert with this no-downgrade rule.
   */
  private static void assertNoDowngradeRule(Bom bom) throws InvalidVersionSpecificationException {

    boolean violationFound = false;
    StringBuilder errorMessages = new StringBuilder();
    Map<String, Artifact> versionlessCoordinatesToArtifact = new HashMap<>();
    for (Artifact artifact : bom.getManagedDependencies()) {
      versionlessCoordinatesToArtifact.put(Artifacts.makeKey(artifact), artifact);
    }
    VersionScheme versionScheme = new GenericVersionScheme();

    for (Artifact artifact : bom.getManagedDependencies()) {
      ClassPathBuilder classPathBuilder = new ClassPathBuilder();
      ClassPathResult result =
          classPathBuilder.resolve(ImmutableList.of(artifact), false, DependencyMediation.MAVEN);

      for (ClassPathEntry entry : result.getClassPath()) {
        Artifact transitiveDependency = entry.getArtifact();
        String dependencyVersionlessCoordinates = Artifacts.makeKey(transitiveDependency);
        Artifact bomArtifact =
            versionlessCoordinatesToArtifact.get(dependencyVersionlessCoordinates);
        if (bomArtifact == null) {
          // transitiveDependency is not part of the BOM
          continue;
        }

        Version versionInBom = versionScheme.parseVersion(bomArtifact.getVersion());
        Version versionInTransitiveDependency =
            versionScheme.parseVersion(transitiveDependency.getVersion());

        if (versionInTransitiveDependency.compareTo(versionInBom) <= 0) {
          // When versionInTransitiveDependency is less than or equal to versionInBom, it satisfies
          // the no-downgrade rule.
          continue;
        }
        // No downgrade rule is violated
        violationFound = true;
        errorMessages.append(
            artifact
                + " has a transitive dependency "
                + transitiveDependency
                + ". This is higher version than "
                + bomArtifact
                + " in the BOM\n");
      }
    }

    Assert.assertFalse(errorMessages.toString(), violationFound);
  }
}
