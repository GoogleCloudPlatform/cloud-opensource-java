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

package com.google.cloud;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.DependencyMediation;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

public class BomContentTest {
  private static VersionScheme versionScheme = new GenericVersionScheme();

  @Test
  public void testLtsBom() throws Exception {
    Path bomPath = Paths.get("..", "cloud-lts-bom", "pom.xml").toAbsolutePath();
    checkBom(bomPath);
  }

  @Test
  public void testLibrariesBom() throws Exception {
    Path bomPath = Paths.get("..", "cloud-oss-bom", "pom.xml").toAbsolutePath();
    checkBom(bomPath);
  }

  private void checkBom(Path bomPath) throws Exception {
    Bom bom = Bom.readBom(bomPath);
    List<Artifact> artifacts = bom.getManagedDependencies();
    for (Artifact artifact : artifacts) {
      assertReachable(buildMavenCentralUrl(artifact));
    }

    assertNoDowngradeRule(bom);
    assertUniqueClasses(artifacts);
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

  /**
   * Asserts that the BOM only provides JARs which contains unique class names to the classpath.
   */
  private static void assertUniqueClasses(List<Artifact> allArtifacts)
      throws InvalidVersionSpecificationException, IOException {

    StringBuilder errorMessageBuilder = new StringBuilder();

    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult result =
        classPathBuilder.resolve(allArtifacts, false, DependencyMediation.MAVEN);

    // A Map of every class name to its artifact ID.
    HashMap<String, String> fullClasspathMap = new HashMap<>();

    for (ClassPathEntry classPathEntry : result.getClassPath()) {
      Artifact currentArtifact = classPathEntry.getArtifact();

      if (!currentArtifact.getGroupId().contains("google")
          || currentArtifact.getGroupId().contains("com.google.android")
          || currentArtifact.getArtifactId().startsWith("grpc-")
          || currentArtifact.getArtifactId().startsWith("proto-")
          || currentArtifact.getArtifactId().equals("protobuf-javalite")
          || currentArtifact.getArtifactId().equals("appengine-testing")) {
        // Skip libraries that produce false positives.
        // See: https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/2226
        continue;
      }

      String artifactCoordinates = Artifacts.toCoordinates(currentArtifact);

      for (String fullyQualifiedClassName : classPathEntry.getFileNames()) {
        if (fullyQualifiedClassName.contains("javax.annotation")
            || fullyQualifiedClassName.contains("$")
            || fullyQualifiedClassName.endsWith("package-info")) {
          // Ignore annotations, nested classes, and package-info files.
          continue;
        }

        String previousArtifact = fullClasspathMap.get(fullyQualifiedClassName);

        if (previousArtifact != null) {
          String msg = String.format(
              "Duplicate class %s found in classpath. Found in artifacts %s and %s.\n",
              fullyQualifiedClassName,
              previousArtifact,
              artifactCoordinates);
          errorMessageBuilder.append(msg);
        } else {
          fullClasspathMap.put(fullyQualifiedClassName, artifactCoordinates);
        }
      }
    }

    String error = errorMessageBuilder.toString();
    Assert.assertTrue(
        "Failing test due to duplicate classes found on classpath:\n" + error, error.isEmpty());
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
   * Asserts that the members of the {@code bom} satisfy the no-downgrade rule. This rule means that
   * the members have the highest versions among the dependencies of them. If there's a violation,
   * users of the BOM would see our BOM downgrading certain dependencies. Downgrading a dependency
   * is bad practice in general because newer versions have more features (classes and methods).
   *
   * <p>For example, if google-http-client 1.40.1 is in the BOM, then no other libraries in the BOM
   * depend on the higher version of the google-http-client.
   *
   * @param bom the BOM to assert with this no-downgrade rule.
   */
  private static void assertNoDowngradeRule(Bom bom) throws InvalidVersionSpecificationException {
    List<String> violations = new ArrayList<>();
    Map<String, Artifact> bomArtifacts = new HashMap<>();
    for (Artifact artifact : bom.getManagedDependencies()) {
      bomArtifacts.put(Artifacts.makeKey(artifact), artifact);
    }

    for (Artifact artifact : bom.getManagedDependencies()) {
      violations.addAll(findNoDowngradeViolation(bomArtifacts, artifact));
    }

    String violationMessage = Joiner.on("\n").join(violations);
    Assert.assertTrue(violationMessage, violations.isEmpty());
  }

  /**
   * Returns messages describing the violation of the no-downgrade rule by {@code artifact} against
   * the BOM containing {@code bomArtifacts}. An empty list if there is no violations.
   */
  private static ImmutableList<String> findNoDowngradeViolation(
      Map<String, Artifact> bomArtifacts, Artifact artifact)
      throws InvalidVersionSpecificationException {
    ImmutableList.Builder<String> violations = ImmutableList.builder();

    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult result =
        classPathBuilder.resolve(ImmutableList.of(artifact), false, DependencyMediation.MAVEN);
    for (ClassPathEntry entry : result.getClassPath()) {
      Artifact transitiveDependency = entry.getArtifact();
      String key = Artifacts.makeKey(transitiveDependency);
      Artifact bomArtifact = bomArtifacts.get(key);
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

      // A violation of the no-downgrade rule is found.
      violations.add(
          artifact
              + " has a transitive dependency "
              + transitiveDependency
              + ". This is higher version than "
              + bomArtifact
              + " in the BOM");
    }
    return violations.build();
  }
}
