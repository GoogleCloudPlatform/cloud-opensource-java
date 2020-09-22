/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.opensource.dependencies;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.File;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.junit.Assert;
import org.junit.Test;

public class RepositoryUtilityTest {
 
  @Test
  public void testFindLocalRepository() {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);
    
    File local = session.getLocalRepository().getBasedir();
    Assert.assertTrue(local.exists());
    Assert.assertTrue(local.canRead());
    Assert.assertTrue(local.canWrite());
  }

  @Test
  public void testFindVersions() throws MavenRepositoryException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    ImmutableList<String> versions =
        RepositoryUtility.findVersions(system, "com.google.cloud", "libraries-bom");
    Truth.assertThat(versions)
        .containsAtLeast("1.1.0", "1.1.1", "1.2.0", "2.0.0", "2.4.0", "2.5.0", "2.6.0")
        .inOrder();
  }

  @Test
  public void testFindHighestVersions()
      throws MavenRepositoryException, InvalidVersionSpecificationException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();

    // FindHighestVersion should work for both jar and pom (extension:pom) artifacts
    for (String artifactId : ImmutableList.of("guava", "guava-bom")) {
      String guavaHighestVersion =
          RepositoryUtility.findHighestVersion(
              system, RepositoryUtility.newSession(system), "com.google.guava", artifactId);
      Assert.assertNotNull(guavaHighestVersion);

      // Not comparing alphabetically; otherwise "100.0" would be smaller than "28.0"
      VersionScheme versionScheme = new GenericVersionScheme();
      Version highestGuava = versionScheme.parseVersion(guavaHighestVersion);
      Version guava28 = versionScheme.parseVersion("28.0");

      Truth.assertWithMessage("Latest guava release is greater than or equal to 28.0")
          .that(highestGuava)
          .isAtLeast(guava28);
    }
  }

  @Test
  public void testFindHighestVersions_nonNumericalVersion()
      throws MavenRepositoryException, InvalidVersionSpecificationException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();

    // google-api-services-bigquery's version does not follow semantic versioning. For example,
    // the latest version as of August 2020 was "v2-rev20200818-1.30.10".
    String bigqueryApiVersion =
        RepositoryUtility.findHighestVersion(
            system,
            RepositoryUtility.newSession(system),
            "com.google.apis",
            "google-api-services-bigquery");
    Assert.assertNotNull(bigqueryApiVersion);

    VersionScheme versionScheme = new GenericVersionScheme();
    Version highestGuava = versionScheme.parseVersion(bigqueryApiVersion);
    Version august2020Version = versionScheme.parseVersion("v2-rev20200818-1.30.10");

    Truth.assertThat(highestGuava).isAtLeast(august2020Version);
  }

  @Test
  public void testMavenRepositoryFromUrl() {
    RemoteRepository remoteRepository =
        RepositoryUtility.mavenRepositoryFromUrl("https://repo.maven.apache.org/maven2");

    assertEquals("https://repo.maven.apache.org/maven2", remoteRepository.getId());
  }
}
