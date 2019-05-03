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

import java.nio.file.Path;
import java.util.Objects;

/**
 * A class in a jar file to uniquely locate the class implementation in a class path.
 */
class ClassInJar {
  private final Path jar;
  private final String className;

  ClassInJar(Path jar, String className) {
    this.jar = jar;
    this.className = className;
  }

  /**
   * Returns jar file containing the class.
   */
  Path getJar() {
    return jar;
  }

  /**
   * Returns class name.
   */
  String getClassName() {
    return className;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClassInJar that = (ClassInJar) o;
    return Objects.equals(jar, that.jar) &&
        Objects.equals(className, that.className);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jar, className);
  }
}
