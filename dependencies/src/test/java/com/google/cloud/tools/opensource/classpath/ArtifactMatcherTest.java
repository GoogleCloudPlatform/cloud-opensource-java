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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ArtifactMatcherTest {
  @Test
  public void testInvalidCoordinates() {
    try {
      new ArtifactMatcher("foo:bar");
      fail();
    } catch (IllegalArgumentException expected) {
      assertEquals(
          "Bad artifact coordinates foo:bar, expected format is"
              + " <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>",
          expected.getMessage());
    }
  }

  @Test
  public void testInvalidNullInput() {
    try {
      new ArtifactMatcher(null);
      fail();
    } catch (NullPointerException expected) {
      // pass
    }
  }

  @Test
  public void testMatchOnCoordinates() {
    ArtifactMatcher matcher = new ArtifactMatcher("foo:bar:1.2.3");
    assertTrue(
        matcher.match(
            new ClassFile(ClassPathEntry.of("foo:bar:no_dep:jar:1.2.3", "dummy.jar"), "foo.Main")));
  }

  @Test
  public void testMatchOnCoordinates_DifferentVersion() {
    ArtifactMatcher matcher = new ArtifactMatcher("foo:bar:1.2.3");
    assertFalse(
        matcher.match(new ClassFile(ClassPathEntry.of("foo:bar:1.2.4", "dummy.jar"), "foo.Main")));
  }
}
