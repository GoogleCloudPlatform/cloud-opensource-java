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

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import java.nio.file.Paths;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class AnnotatedJarTest {

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(
            new AnnotatedJar(Paths.get("foo"), new DefaultArtifact("com.foo:bar:1.0.0")),
            new AnnotatedJar(Paths.get("foo"), new DefaultArtifact("com.foo:bar:1.0.0")))
        .addEqualityGroup(
            new AnnotatedJar(Paths.get("foo"), null),
            new AnnotatedJar(Paths.get("foo"), null))
        .addEqualityGroup(
            new AnnotatedJar(Paths.get("foo", "bar"), null),
            new AnnotatedJar(Paths.get("foo", "bar"), null))
        .testEquals();
  }

}
