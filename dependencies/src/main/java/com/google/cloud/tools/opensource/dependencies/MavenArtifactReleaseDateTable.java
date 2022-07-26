package com.google.cloud.tools.opensource.dependencies;

import com.google.common.collect.ImmutableList;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;

/**
 * Run this class with "com.google.cloud:google-cloud-bom:0.176.0" argument. It prints CSV of
 * the library versions and release date.
 */
public class MavenArtifactReleaseDateTable {

  static private RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();

  public static void main(String[] arguments) throws Exception {

    String bomCoordinates = arguments[0];
    Bom bom = Bom.readBom(bomCoordinates);

    System.out.println(
        "serviceName,version,maven_coordinates,release_date"
    );
    for (Artifact managedDependency : bom.getManagedDependencies()) {
      String groupId = managedDependency.getGroupId();
      String artifactId = managedDependency.getArtifactId();
      if (!artifactId.startsWith("google-cloud-")) {
        continue;
      }
      if (artifactId.endsWith("emulator")) {
        continue;
      }
      ImmutableList<String> versions = RepositoryUtility.findVersions(repositorySystem,
          groupId, artifactId
      );
      for (String version : versions) {
        if (version.endsWith("SNAPSHOT")) {
          // "SNAPSHOT" comes from local Maven repository
          continue;
        }
        String url = buildMavenCentralJarUrl(groupId, artifactId, version);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        long lastModifiedMillis = connection.getLastModified();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(lastModifiedMillis);
        String serviceName = convertToServiceName(artifactId);
        String dateValue = String.format("%s-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH));
        System.out.println(
            serviceName + "," +
                version + "," +
            (groupId + ":" + artifactId + ":" + version) + "," +
            dateValue
        );
      }
    }
  }

  /**
   * Converts the artifactId to service name. Usually just removing the "google-cloud-" prefix,
   * except certain libraries with ad-hoc conversion.
   */
  private static String convertToServiceName(String artifactId) {
    if ("google-cloud-storage".equals(artifactId) || "google-cloud-nio".equals(artifactId)) {
      return "bigstore";
    }
    return artifactId.replace("google-cloud-", "");
  }

  private static String buildMavenCentralJarUrl(String groupId,
      String artifactId, String version) {
    return "https://repo1.maven.org/maven2/"
        +groupId.replace('.', '/')
        + "/"
        + artifactId
        + "/"
        + version
        + "/"
        + artifactId + "-" + version + ".jar";
  }
}
