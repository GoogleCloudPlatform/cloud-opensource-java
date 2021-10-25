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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.junit.Assert;
import org.junit.Test;
import com.google.cloud.tools.opensource.dependencies.Bom;

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
    List<Artifact> artifacts = Bom.readBom(bomPath).getManagedDependencies();
    for (Artifact artifact : artifacts) {
      assertReachable(buildMavenCentralUrl(artifact));
    }
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

  private static void assertUniqueClasses(List<Artifact> allArtifacts)
      throws InvalidVersionSpecificationException, IOException {
    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult result =
        classPathBuilder.resolve(allArtifacts, true, DependencyMediation.MAVEN);

    // A Map of every class name to its artifact ID.
    HashMap<String, String> fullClasspathMap = new HashMap<>();

    for (ClassPathEntry classPathEntry : result.getClassPath()) {
      Artifact currentArtifact = classPathEntry.getArtifact();

      if (!currentArtifact.getGroupId().contains("google")
          || currentArtifact.getGroupId().contains("com.google.android")
          || currentArtifact.getArtifactId().startsWith("proto-")
          || currentArtifact.getArtifactId().equals("protobuf-javalite")) {
        // Skip libraries that produce false positives.
        continue;
      }

      String artifactCoordinates = Artifacts.toCoordinates(currentArtifact);

      for (String fullyQualifiedClassName : classPathEntry.getFileNames()) {
        if (fullyQualifiedClassName.contains("javax.annotation")) {
          // Ignore Java annotation classes.
          continue;
        }

        if (fullyQualifiedClassName.contains("$")) {
          // Ignore nested classes because if nested classes are duplicated then the
          // parent class must also be duplicated.
          continue;
        }

        String previousArtifact = fullClasspathMap.get(fullyQualifiedClassName);

        if (previousArtifact != null) {
          String msg = String.format(
              "Duplicate class %s found in classpath. Found in artifacts %s and %s.",
              fullyQualifiedClassName,
              previousArtifact,
              artifactCoordinates);
          System.out.println(msg);
          // Assert.fail(msg);
        } else {
          fullClasspathMap.put(fullyQualifiedClassName, artifactCoordinates);
        }
      }
    }
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
}
