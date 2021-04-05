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

import static com.google.cloud.tools.opensource.dependencies.RepositoryUtility.CENTRAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.EqualsTester;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.Assert;
import org.junit.Test;

public class ClassPathEntryTest {
  private Path fooJar = Paths.get("foo.jar");
  private Path barJar = Paths.get("bar.jar");
  private Artifact fooArtifact =
      new DefaultArtifact("com.google", "foo", null, "jar", "0.0.1", null, fooJar.toFile());
  private Artifact barArtifact =
      new DefaultArtifact("com.google", "bar", null, "jar", "0.0.1", null, barJar.toFile());

  @Test
  public void testCreationJar() {
    ClassPathEntry entry = new ClassPathEntry(fooJar);
    assertEquals(fooJar, entry.getJar());
    assertNull(entry.getArtifact());
  }

  @Test
  public void testCreationArtifact() {
    ClassPathEntry entry = new ClassPathEntry(fooArtifact);
    assertEquals(fooJar, entry.getJar());
    assertEquals(entry.getArtifact(), fooArtifact);
  }

  @Test
  public void testEquality() {
    Path jar1 = Paths.get("1.jar");
    Path jar2 = Paths.get("2.jar");
    new EqualsTester()
        .addEqualityGroup(new ClassPathEntry(jar1), new ClassPathEntry(jar1))
        .addEqualityGroup(new ClassPathEntry(jar2), new ClassPathEntry(jar2))
        .addEqualityGroup(new ClassPathEntry(fooArtifact), new ClassPathEntry(fooArtifact))
        .addEqualityGroup(new ClassPathEntry(barArtifact), new ClassPathEntry(barArtifact))
        .addEqualityGroup(
            new ClassPathEntry(
                new DefaultArtifact(null, null, null, null, null, null, jar1.toFile())))
        .testEquals();
  }

  @Test
  public void testToStringJar() {
    ClassPathEntry entry = new ClassPathEntry(fooJar);
    assertEquals("foo.jar", entry.toString());
  }

  @Test
  public void testToStringArtifact() {
    ClassPathEntry entry = new ClassPathEntry(fooArtifact);
    assertEquals("com.google:foo:0.0.1", entry.toString());
  }

  @Test
  public void testGetClassNames() throws IOException, ArtifactResolutionException {
    // copy into the local repository so we can read the jar file
    Artifact artifact = resolveArtifact("com.google.truth.extensions:truth-java8-extension:1.1");
    
    ClassPathEntry entry = new ClassPathEntry(artifact);
    ImmutableSet<String> classFileNames = entry.getFileNames();
    
    Truth.assertThat(classFileNames).containsExactly(
        "com.google.common.truth.IntStreamSubject",
        "com.google.common.truth.LongStreamSubject",
        "com.google.common.truth.OptionalDoubleSubject",
        "com.google.common.truth.OptionalSubject",
        "com.google.common.truth.OptionalIntSubject",
        "com.google.common.truth.OptionalLongSubject",
        "com.google.common.truth.PathSubject",
        "com.google.common.truth.Truth8",
        "com.google.common.truth.StreamSubject");
  }
  
  @Test
  public void testGetClassNames_innerClasses()
      throws IOException, ArtifactResolutionException, URISyntaxException {

    ClassPathEntry entry = TestHelper.classPathEntryOfResource(
        "testdata/conscrypt-openjdk-uber-1.4.2.jar");
    ImmutableSet<String> classFileNames = entry.getFileNames();
    Truth.assertThat(classFileNames).containsAtLeast(
        "org.conscrypt.OpenSSLSignature$1",
        "org.conscrypt.OpenSSLContextImpl$TLSv1",
        "org.conscrypt.TrustManagerImpl$1",
        "org.conscrypt.PeerInfoProvider",
        "org.conscrypt.PeerInfoProvider$1",
        "org.conscrypt.ExternalSession$Provider",
        "org.conscrypt.OpenSSLMac");
  }
  
  @Test
  public void testGetClassNames_noManifest()
      throws IOException, ArtifactResolutionException, URISyntaxException {

    ClassPathEntry entry = TestHelper.classPathEntryOfResource(
        "testdata/conscrypt-openjdk-uber-1.4.2.jar");
    ImmutableSet<String> classFileNames = entry.getFileNames();
    for (String filename : classFileNames) {
      Assert.assertFalse(filename.toLowerCase(Locale.ENGLISH).contains("manifest"));
      Assert.assertFalse(filename.toLowerCase(Locale.ENGLISH).contains("meta"));
    }  
  }

  private static Artifact resolveArtifact(String coordinates) throws ArtifactResolutionException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);

    Artifact artifact = new DefaultArtifact(coordinates);    
    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.addRepository(CENTRAL);
    artifactRequest.setArtifact(artifact);
    ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
    
    return artifactResult.getArtifact();
  }
  
  @Test
  public void testFilePresenceRequirement() {
    Artifact artifactWithoutFile = new DefaultArtifact("com.google:foo:jar:1.0.0");
    try {
      new ClassPathEntry(artifactWithoutFile);
      fail();
    } catch (NullPointerException expected) {
    }
  }
}
