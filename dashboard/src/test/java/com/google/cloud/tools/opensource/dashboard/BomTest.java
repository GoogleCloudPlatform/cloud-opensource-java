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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.junit.Assert;
import org.junit.Test;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;

public class BomTest {
  
  @Test
  public void testLtsBom()
      throws IOException, MavenRepositoryException {
    Path bomPath = Paths.get("..", "boms", "cloud-lts-bom", "pom.xml").toAbsolutePath();  
    checkBom(bomPath);
  }
  
  @Test
  public void testLibrariesBom()
      throws IOException, MavenRepositoryException {
    Path bomPath = Paths.get("..", "boms", "cloud-oss-bom", "pom.xml").toAbsolutePath();  
    checkBom(bomPath);
  }

  private void checkBom(Path bomPath) throws MavenRepositoryException, IOException {
    List<Artifact> artifacts = Bom.readBom(bomPath).getManagedDependencies();
    for (Artifact artifact : artifacts) {
      assertReachable(buildMavenCentralUrl(artifact));
    }
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
}
