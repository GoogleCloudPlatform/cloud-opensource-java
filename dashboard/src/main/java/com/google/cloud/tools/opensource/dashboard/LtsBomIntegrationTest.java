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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.common.io.CharStreams;

class LtsBomIntegrationTest {
  
  // todo immutable
  private static final Map<String, String> bomVersions = new HashMap<>();

  public static void main(String[] args) 
      throws MavenRepositoryException, IOException {
    Path bomPath = Paths.get("..", "boms", "cloud-lts-bom", "pom.xml").toAbsolutePath();  
    List<Artifact> managedDependencies = Bom.readBom(bomPath).getManagedDependencies();
    
    for (Artifact artifact : managedDependencies) {
      bomVersions.put(Artifacts.makeKey(artifact), artifact.getVersion());
    }
    
    for (Artifact artifact : managedDependencies) {
      String testJar = buildTestJarUrl(artifact);
      HttpURLConnection connection = (HttpURLConnection) new URL(testJar).openConnection();
      connection.setRequestMethod("HEAD");
      int result = connection.getResponseCode();
      if (result == HttpURLConnection.HTTP_OK) {
        testArtifact(artifact);
      } else {
        System.err.println(artifact + " does not have test jar");        
      }
    }
  }

  private static void testArtifact(Artifact artifact) throws IOException {
    // download the pom.xml from Maven Central
    URL remote = buildPomUrl(artifact);
    Path temp = Files.createTempDirectory("pom");
    Path local = temp.resolve("pom.xml");
    Files.copy(remote.openStream(), local);
    
    // todo how to locate maven?
    ProcessBuilder builder = new ProcessBuilder("/opt/java/maven/bin/mvn", "dependency:list");
    builder.directory(temp.toFile());
    Process process = builder.start();
    
    // todo what charset does maven use?
    InputStreamReader reader = new InputStreamReader(process.getInputStream());
    
    CoordinatesExtractor processor = new CoordinatesExtractor();
    CharStreams.readLines(reader, processor);
    
    for (String coordinates : processor.getResult()) {
      System.out.println(coordinates);
      DefaultArtifact original = new DefaultArtifact(coordinates);
      String key = Artifacts.makeKey(original);
      String newVersion = bomVersions.get(key);
      if (newVersion != null) {
        coordinates = key + ":" + newVersion;
      }
      DefaultArtifact modified = new DefaultArtifact(coordinates);
      System.out.println(modified);
    }
    
    // make pom.xml to run tests
    
    // run tests
  }
  
  private static String buildTestJarUrl(Artifact artifact) {
    String fileName = artifact.getArtifactId() + "-" + artifact.getVersion() + "-tests.jar";
    return MavenCentral.buildUrl(artifact) + fileName;
  }
  
  private static URL buildPomUrl(Artifact artifact) throws MalformedURLException {
    String fileName = artifact.getArtifactId() + "-" + artifact.getVersion() + ".pom"; 
    return new URL(MavenCentral.buildUrl(artifact) + fileName);
  }

}
