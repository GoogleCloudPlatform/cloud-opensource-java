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
import static org.junit.Assert.assertNull;

import com.google.common.testing.EqualsTester;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class ClassPathEntryTest {

  @Test
  public void testCreation() {
    Path jar = Paths.get("foo.jar");
    ClassPathEntry entry = new ClassPathEntry(jar);
    assertEquals(jar.toAbsolutePath().toString(), entry.getPath());
    assertNull(entry.getArtifact());
  }

  @Test
  public void testEquality() {
    Path jar1 = Paths.get("1.jar");
    Path jar2 = Paths.get("2.jar");
    new EqualsTester()
        .addEqualityGroup(new ClassPathEntry(jar1), new ClassPathEntry(jar1))
        .addEqualityGroup(new ClassPathEntry(jar2), new ClassPathEntry(jar2))
        .addEqualityGroup(
            new ArtifactClassPathEntry(
                new DefaultArtifact(null, null, null, null, null, null, jar1.toFile())))
        .testEquals();
  }

  @Test
  public void testToString() {
    Path fooJar = Paths.get("foo.jar");
    ClassPathEntry entry = new ClassPathEntry(fooJar);
    assertEquals("JAR(foo.jar)", entry.toString());
  }
}
