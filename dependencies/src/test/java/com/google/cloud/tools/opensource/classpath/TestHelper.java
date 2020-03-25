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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Utility used among tests. */
public class TestHelper {

  private TestHelper() {}

  /** Returns an absolute path of {@code resourceName}. */
  public static Path absolutePathOfResource(String resourceName) throws URISyntaxException {
    return Paths.get(ClassLoader.getSystemResource(resourceName).toURI()).toAbsolutePath();
  }

  public static ClassPathEntry classPathEntryOfResource(String resourceName)
      throws URISyntaxException {
    return new ClassPathEntry(absolutePathOfResource(resourceName));
  }
}
