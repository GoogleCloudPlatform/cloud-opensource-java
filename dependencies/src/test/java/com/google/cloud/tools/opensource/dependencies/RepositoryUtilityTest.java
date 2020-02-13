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

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;
import static com.google.common.truth.Correspondence.transforming;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
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
  public void testReadBom_coordinates() throws ArtifactDescriptorException {
    Bom bom = RepositoryUtility.readBom("com.google.cloud:google-cloud-bom:0.61.0-alpha");
    List<Artifact> managedDependencies = bom.getManagedDependencies();
    // Characterization test. As long as the artifact doesn't change (and it shouldn't)
    // the answer won't change.
    Assert.assertEquals(134, managedDependencies.size());
    Assert.assertEquals("com.google.cloud:google-cloud-bom:0.61.0-alpha", bom.getCoordinates());
  }

  @Test
  public void testReadBom_coordinates_invalidRepository() {
    // This version is so old that it's very unlikely we have it in the Maven local cache,
    // but it exists in Maven central.
    String coordinates = "com.google.cloud:google-cloud-bom:pom:0.32.0-alpha";
    try {
      RepositoryUtility.readBom(coordinates, ImmutableList.of("http://nonexistent.example.com"));
      fail("readBom should not access Maven Central when it's not in the repository list.");
    } catch (ArtifactDescriptorException ex) {
      assertEquals("Failed to read artifact descriptor for " + coordinates, ex.getMessage());
    }
  }

  @Test
  public void testReadBom_path()
      throws MavenRepositoryException, ArtifactDescriptorException, URISyntaxException {
    Path pomFile = absolutePathOfResource("libraries-bom-2.7.0.pom");
    Bom bomFromFile = RepositoryUtility.readBom(pomFile);
    ImmutableList<Artifact> artifactsFromFile = bomFromFile.getManagedDependencies();

    // Compare the result with readBom(String coordinates)
    String expectedBomCoordinates = "com.google.cloud:libraries-bom:2.7.0";
    Bom expectedBom = RepositoryUtility.readBom(expectedBomCoordinates);
    ImmutableList<Artifact> expectedArtifacts = expectedBom.getManagedDependencies();

    Truth.assertThat(bomFromFile.getCoordinates()).isEqualTo(expectedBomCoordinates);
    Truth.assertThat(artifactsFromFile)
        .comparingElementsUsing(
            transforming(
                Artifacts::toCoordinates, Artifacts::toCoordinates, "has Maven coordinates"))
        .containsExactlyElementsIn(expectedArtifacts)
        .inOrder();
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
}
