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

import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class TestHelper {

  static Path createProjectCopy(String resourceName) throws IOException, URISyntaxException {
    URI uri = ClassLoader.getSystemResource(resourceName).toURI();
    Path sourceProject = Paths.get(uri).toAbsolutePath();
    Path copiedProject = Files.createTempDirectory("lts-test");

    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(sourceProject);
    for (Path path : paths) {

      Path relativePath = sourceProject.relativize(path);
      Path newPath = copiedProject.resolve(relativePath);
      if (path.toFile().isDirectory()) {
        if (!newPath.toFile().exists()) {
          Files.createDirectory(newPath);
        }
      } else {
        Files.copy(path, newPath);
      }
    }

    return copiedProject;
  }
}
