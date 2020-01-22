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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A locator for a compiled class file of {@code className} in {@code jar} to uniquely locate the
 * class implementation in a class path.
 */
public final class ClassFile {
  private final Path jar;
  private final String binaryName;

  @VisibleForTesting
  public ClassFile(Path jar, String className) {
    this.jar = checkNotNull(jar);
    this.binaryName = checkNotNull(className);
  }

  /** Returns the path to the JAR file containing the class. */
  public Path getJar() {
    return jar;
  }

  /**
   * Returns the binary name of the class as defined by the 
   * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.1">Java
   * Language Specification</a> and found in the .class file.
   */
  public String getBinaryName() {
    return binaryName;
  }

  /**
   * Returns the outer class if this class is an inner class.
   * Otherwise returns the instance itself.
   */
  ClassFile topLevelClassFile() {
    return binaryName.contains("$") ? new ClassFile(jar, binaryName.split("\\$")[0]) : this;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ClassFile that = (ClassFile) other;
    return Objects.equals(jar, that.jar) && Objects.equals(binaryName, that.binaryName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jar, binaryName);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("jar", jar)
        .add("className", binaryName)
        .toString();
  }
}
