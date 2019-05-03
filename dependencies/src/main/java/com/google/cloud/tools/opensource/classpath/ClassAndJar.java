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

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A tuple of a class name and a jar file to uniquely locate the class implementation in a class
 * path.
 */
final class ClassAndJar {
  private final Path jar;
  private final String className;

  ClassAndJar(Path jar, String className) {
    this.jar = checkNotNull(jar);
    this.className = checkNotNull(className);
  }

  /** Returns jar file containing the class. */
  Path getJar() {
    return jar;
  }

  /** Returns class name (binary name as in {@link Symbol#getClassName()}) */
  String getClassName() {
    return className;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ClassAndJar that = (ClassAndJar) other;
    return Objects.equals(jar, that.jar) && Objects.equals(className, that.className);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jar, className);
  }
}
