/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.opensource.lts;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Modifies build.gradle files to use the libraries in the BOM when running the unit tests. */
class GradleProjectModifier implements BuildFileModifier {
  @Override
  public void modifyFiles(String name, Path projectDirectory, Bom bom)
      throws TestSetupFailureException {
    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(projectDirectory);
    try {
      for (Path path : paths) {
        if (path.endsWith("build.gradle")) {
          modifyGradleFile(path, bom);
        }
      }
    } catch (IOException ex) {
      throw new TestSetupFailureException("Unable to modify the build file", ex);
    }
  }

  private static void modifyGradleFile(Path gradleFile, Bom bom) throws IOException {
    String buildGradleContent =
        Files.asCharSource(gradleFile.toFile(), StandardCharsets.UTF_8).read();

    String bomCoordinates = bom.getCoordinates();

    buildGradleContent =
        buildGradleContent.replaceAll(
            "\ndependencies \\{",
            "\ndependencies {\n    testRuntime enforcedPlatform('" + bomCoordinates + "')");

    com.google.common.io.Files.asCharSink(gradleFile.toFile(), Charsets.UTF_8)
        .write(buildGradleContent);
  }
}
