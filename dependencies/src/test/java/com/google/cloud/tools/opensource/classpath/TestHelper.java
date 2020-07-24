/*
 * Copyright 2019 Google LLC.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/** Utility used among tests. */
public class TestHelper {

  private TestHelper() {}

  /** Returns an absolute path of {@code resourceName}. */
  public static Path absolutePathOfResource(String resourceName) throws URISyntaxException {
    return Paths.get(ClassLoader.getSystemResource(resourceName).toURI()).toAbsolutePath();
  }

  public static ClassPathEntry classPathEntryOfResource(String resourceName)
      throws URISyntaxException, IOException {
    ClassPathEntry entry = new ClassPathEntry(absolutePathOfResource(resourceName));
    return entry;
  }

  static final Correspondence<Path, String> PATH_FILE_NAMES =
      Correspondence.from(
          (actual, expected) -> actual.getFileName().toString().equals(expected),
          "has file name equal to");

  static final Correspondence<ClassPathEntry, String> COORDINATES =
      Correspondence.from(
          (actual, expected) -> Artifacts.toCoordinates(actual.getArtifact()).equals(expected),
          "has Maven coordinates equal to");

  /** Returns the class path for the full dependency tree of {@code coordinates}. */
  static ImmutableList<ClassPathEntry> resolve(String... coordinates) throws IOException {
    ImmutableList<Artifact> artifacts =
        Arrays.stream(coordinates).map(DefaultArtifact::new).collect(toImmutableList());
    ClassPathResult result = (new ClassPathBuilder()).resolve(artifacts, true);
    return result.getClassPath();
  }
}
