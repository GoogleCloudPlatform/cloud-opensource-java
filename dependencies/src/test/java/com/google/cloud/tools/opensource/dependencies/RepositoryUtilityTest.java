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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.io.Files.asCharSink;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.readLines;
import static com.google.common.reflect.Reflection.getPackageName;
import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
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

  private static final String UPDATE_GOLDEN_ARTIFACTS = "UPDATE_GOLDEN_ARTIFACTS";
  private static final Path GOLDEN_FILE = goldenFile();

  private static Path goldenFile() {
    Path goldenFile = Paths.get("src", "test", "resources");
    for (String dir : Splitter.on('.').split(getPackageName(RepositoryUtilityTest.class))) {
      goldenFile = goldenFile.resolve(dir);
    }
    return goldenFile.resolve("goldenBomArtifacts.txt");
  }

  @Test
  public void testReadBom_path() throws MavenRepositoryException, IOException {
    Path pomFile = Paths.get("..", "boms", "cloud-oss-bom", "pom.xml");

    Bom currentBom = RepositoryUtility.readBom(pomFile);

    String coordinates = currentBom.getCoordinates();
    assertThat(coordinates).startsWith("com.google.cloud:libraries-bom:");
    assertThat(coordinates).endsWith("-SNAPSHOT");

    // Assert that the managed dependencies haven't unexpectedly changed.
    // If this fails, update the golden file so the changes can be reviewed by running
    // this test with UPDATE_GOLDEN_ARTIFACTS set to any nonempty string. Then
    // rerun the test without that.

    if (shouldUpdateGoldenArtifactsFile()) {
      updateGoldenArtifactsFileAndFail(currentBom.getManagedDependencies());
    }

    assertWithMessage(
            "If BOM artifacts have changed, rerun this test setting the environment variable %s"
                + " and then try again.",
            UPDATE_GOLDEN_ARTIFACTS)
        .that(currentBom.getManagedDependencies())
        .comparingElementsUsing(transforming(Artifacts::toCoordinates, "has Maven coordinates"))
        .containsExactlyElementsIn(loadExpectedArtifacts());
  }

  private static boolean shouldUpdateGoldenArtifactsFile() {
    return !isNullOrEmpty(System.getenv(UPDATE_GOLDEN_ARTIFACTS));
  }

  private static void updateGoldenArtifactsFileAndFail(ImmutableList<Artifact> artifacts)
      throws IOException {
    asCharSink(GOLDEN_FILE.toFile(), UTF_8)
        .writeLines(
            Stream.concat(
                Stream.of(
                    "# Automatically generated by running tests with environment variable",
                    "# " + UPDATE_GOLDEN_ARTIFACTS + " set.",
                    "# Used by " + RepositoryUtilityTest.class.getName() + ".",
                    ""),
                artifacts.stream().map(Artifacts::toCoordinates).sorted()));
    fail(
        String.format(
            "Wrote new golden file at %s.\nRerun %s without setting %s.",
            GOLDEN_FILE, RepositoryUtilityTest.class.getName(), UPDATE_GOLDEN_ARTIFACTS));
  }

  private static Iterable<String> loadExpectedArtifacts() throws IOException {
    return filter(
        readLines(
            getResource(RepositoryUtilityTest.class, GOLDEN_FILE.getFileName().toString()), UTF_8),
        line -> !line.isEmpty() && !line.startsWith("#"));
  }

  @Test
  public void testFindVersions() throws MavenRepositoryException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    ImmutableList<String> versions =
        RepositoryUtility.findVersions(system, "com.google.cloud", "libraries-bom");
    assertThat(versions)
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
