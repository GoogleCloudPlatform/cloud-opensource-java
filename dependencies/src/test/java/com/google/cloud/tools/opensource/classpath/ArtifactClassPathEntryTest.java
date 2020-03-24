/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.opensource.classpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.testing.EqualsTester;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class ArtifactClassPathEntryTest {
  Path fooJar = Paths.get("foo.jar");
  Path barJar = Paths.get("bar.jar");
  private Artifact fooArtifact =
      new DefaultArtifact("com.google", "foo", null, "jar", "0.0.1", null, fooJar.toFile());
  private Artifact barArtifact =
      new DefaultArtifact("com.google", "bar", null, "jar", "0.0.1", null, barJar.toFile());

  @Test
  public void testCreation() {
    Path jar = Paths.get("foo.jar");
    ArtifactClassPathEntry entry = new ArtifactClassPathEntry(fooArtifact);
    assertEquals(jar.toAbsolutePath().toString(), entry.getClassPath());

    assertEquals(fooArtifact, entry.getArtifact());
  }

  @Test
  public void testEquality() {
    new EqualsTester()
        .addEqualityGroup(
            new ArtifactClassPathEntry(fooArtifact),
            new ArtifactClassPathEntry(
                new DefaultArtifact(
                    "com.google", "foo", null, "jar", "0.0.1", null, fooJar.toFile())))
        .addEqualityGroup(new ArtifactClassPathEntry(barArtifact))
        .addEqualityGroup(new JarClassPathEntry(fooJar))
        .testEquals();
  }

  @Test
  public void testFilePresenceRequirement() {
    Artifact artifactWithoutFile = new DefaultArtifact("com.google:foo:jar:1.0.0");
    try {
      new ArtifactClassPathEntry(artifactWithoutFile);
      fail();
    } catch (NullPointerException expected) {
      // pass
    }
  }

  @Test
  public void testToString() {
    ClassPathEntry entry = new ArtifactClassPathEntry(fooArtifact);
    assertEquals("Artifact(com.google:foo:jar:0.0.1)", entry.toString());
  }
}
