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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.junit.Assert;
import org.junit.Test;

public class RepositoryUtilityTest {

  private VersionScheme versionScheme = new GenericVersionScheme();

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
  public void testReadBom_path() throws MavenRepositoryException {
    Path pomFile = Paths.get("..", "boms", "cloud-oss-bom", "pom.xml");
    
    Bom bom = RepositoryUtility.readBom(pomFile);
    
    ImmutableList<Artifact> artifacts = bom.getManagedDependencies();
    Assert.assertEquals(209, artifacts.size());
    String coordinates = bom.getCoordinates();
    Assert.assertTrue(coordinates.startsWith("com.google.cloud:libraries-bom:"));
    Assert.assertTrue(coordinates.endsWith("-SNAPSHOT"));
  }

  @Test
  public void testFindVersions() throws MavenRepositoryException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    ImmutableList<String> versions =
        RepositoryUtility.findVersions(system, "com.google.cloud", "libraries-bom");
    Truth.assertThat(versions).containsAtLeast("1.1.0", "1.1.1", "1.2.0", "2.0.0").inOrder();
  }

  @Test
  public void testFindHighestVersion()
      throws MavenRepositoryException, InvalidVersionSpecificationException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();

    // FindHighestVersion should work for both jar and pom (extension:pom) artifacts
    for (String artifactId : ImmutableList.of("guava", "guava-bom")) {
      String guavaHighestVersion =
          RepositoryUtility.findHighestVersion(
              system, RepositoryUtility.newSession(system), "com.google.guava", artifactId);
      Assert.assertNotNull(guavaHighestVersion);

      // Not comparing alphabetically; otherwise "100.0" would be smaller than "28.0"
      Version highestGuava = versionScheme.parseVersion(guavaHighestVersion);
      Version guava28 = versionScheme.parseVersion("28.0");

      Truth.assertWithMessage("Latest guava release is greater than or equal to 28.0")
          .that(highestGuava)
          .isAtLeast(guava28);
    }
  }

  @Test
  public void testFindHighestVersion_secondHighestSnapshot()
      throws MavenRepositoryException, InvalidVersionSpecificationException,
          VersionRangeResolutionException {
    // Protobuf repository keeps the latest released version in its master without incrementing
    // its version. RepositorySystem.resolveVersionRange considers version "X.Y.Z" is higher than
    // "X.Y.Z-SNAPSHOT". Therefore, RepositoryUtility.findHighestVersion needs to take this into
    // account to pick up the version from master branch ("X.Y.Z-SNAPSHOT").
    RepositorySystem mockSystem = mock(RepositorySystem.class);

    VersionRangeResult protobufSnapshotVersionResult =
        new VersionRangeResult(new VersionRangeRequest());

    // Mock response that simulates RepositorySystem.resolveVersionRange returning 3.9.1 as highest
    // rather than 3.9.1-SNAPSHOT.
    protobufSnapshotVersionResult.setVersions(
        ImmutableList.of(
            versionScheme.parseVersion("3.6.0"),
            versionScheme.parseVersion("3.9.1-SNAPSHOT"), // HEAD
            versionScheme.parseVersion("3.9.1"))); // latest in Maven central
    when(mockSystem.resolveVersionRange(
            any(RepositorySystemSession.class),
            argThat(request -> "protobuf-java".equals(request.getArtifact().getArtifactId()))))
        .thenReturn(protobufSnapshotVersionResult);

    String highestVersion =
        RepositoryUtility.findHighestVersion(
            mockSystem,
            mock(RepositorySystemSession.class),
            "com.google.protobuf",
            "protobuf-java");

    Assert.assertEquals("3.9.1-SNAPSHOT", highestVersion);
  }

  @Test
  public void testFindHighestVersion_onlyOneVersion()
      throws MavenRepositoryException, InvalidVersionSpecificationException,
          VersionRangeResolutionException {
    RepositorySystem mockSystem = mock(RepositorySystem.class);

    VersionRangeResult protobufSnapshotVersionResult =
        new VersionRangeResult(new VersionRangeRequest());

    protobufSnapshotVersionResult.setVersions(
        ImmutableList.of(versionScheme.parseVersion("3.9.1")));
    when(mockSystem.resolveVersionRange(
            any(RepositorySystemSession.class),
            argThat(request -> "protobuf-java".equals(request.getArtifact().getArtifactId()))))
        .thenReturn(protobufSnapshotVersionResult);

    String highestVersion =
        RepositoryUtility.findHighestVersion(
            mockSystem,
            mock(RepositorySystemSession.class),
            "com.google.protobuf",
            "protobuf-java");

    Assert.assertEquals("3.9.1", highestVersion);
  }
}
