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
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.common.io.ByteStreams;

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
    ByteStreams.copy(process.getInputStream(), System.out);
    
    // make pom.xml to run tests
    
    // run tests
  }
  

  // todo static utility method to share
  private static String buildMavenCentralUrl(Artifact artifact) {
        
    return "https://repo1.maven.org/maven2/"
        + artifact.getGroupId().replace('.', '/')
        + "/"
        + artifact.getArtifactId()
        + "/"
        + artifact.getVersion()
        + "/";
  }
  
  private static String buildTestJarUrl(Artifact artifact) {
    
    String fileName = artifact.getArtifactId() + "-" + artifact.getVersion() + "-tests.jar";
    
    return buildMavenCentralUrl(artifact) + fileName;
  }
  
  private static URL buildPomUrl(Artifact artifact) throws MalformedURLException {
    
    String fileName = artifact.getArtifactId() + "-" + artifact.getVersion() + ".pom";
    
    return new URL(buildMavenCentralUrl(artifact) + fileName);
  }



}
