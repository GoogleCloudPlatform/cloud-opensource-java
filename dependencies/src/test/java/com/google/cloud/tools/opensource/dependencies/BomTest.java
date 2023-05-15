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

import static com.google.common.truth.Correspondence.transforming;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.cloud.tools.opensource.classpath.TestHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.junit.Assert;
import org.junit.Test;

public class BomTest {
  
  @Test
  public void testReadBom_coordinates() throws ArtifactDescriptorException {
    Bom bom = Bom.readBom("com.google.cloud:google-cloud-bom:0.61.0-alpha");
    List<Artifact> managedDependencies = bom.getManagedDependencies();
    // Characterization test. As long as the artifact doesn't change (and it shouldn't)
    // the answer won't change.
    Assert.assertEquals(136, managedDependencies.size());
    Assert.assertEquals("com.google.cloud:google-cloud-bom:0.61.0-alpha", bom.getCoordinates());
  }

  @Test
  public void testReadBom_coordinates_invalidRepository() {
    // This version is so old that it's very unlikely we have it in the Maven local cache,
    // but it exists in Maven central.
    String coordinates = "com.google.cloud:google-cloud-bom:pom:0.32.0-alpha";
    try {
      Bom.readBom(coordinates, ImmutableList.of("http://nonexistent.example.com"));
      fail("readBom should not access Maven Central when it's not in the repository list.");
    } catch (ArtifactDescriptorException ex) {
      assertEquals("Failed to read artifact descriptor for " + coordinates, ex.getMessage());
    }
  }

  @Test
  public void testReadBom_path()
      throws MavenRepositoryException, ArtifactDescriptorException, URISyntaxException {
    Path pomFile = TestHelper.absolutePathOfResource("libraries-bom-2.7.0.pom");
    Bom bomFromFile = Bom.readBom(pomFile);
    ImmutableList<Artifact> artifactsFromFile = bomFromFile.getManagedDependencies();

    // Compare the result with readBom(String coordinates)
    String expectedBomCoordinates = "com.google.cloud:libraries-bom:2.7.0";
    Bom expectedBom = Bom.readBom(expectedBomCoordinates);
    ImmutableList<Artifact> expectedArtifacts = expectedBom.getManagedDependencies();

    Truth.assertThat(bomFromFile.getCoordinates()).isEqualTo(expectedBomCoordinates);
    Truth.assertThat(artifactsFromFile)
        .comparingElementsUsing(
            transforming(
                Artifacts::toCoordinates, Artifacts::toCoordinates, "has Maven coordinates"))
        .containsExactlyElementsIn(expectedArtifacts)
        .inOrder();
  }

}
