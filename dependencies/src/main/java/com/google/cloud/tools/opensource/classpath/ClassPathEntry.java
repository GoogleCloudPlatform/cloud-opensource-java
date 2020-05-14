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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.bcel.classfile.JavaClass;
import org.eclipse.aether.artifact.Artifact;

/** An entry in a class path. */
public final class ClassPathEntry {

  private Path jar;
  private Artifact artifact;
  private ImmutableSet<String> classFileNames;

  /** An entry for a JAR file without Maven coordinates. */
  ClassPathEntry(Path jar) {
    this.jar = checkNotNull(jar);
  }

  /** 
   * An entry for a Maven artifact. 
   * 
   * @throws NullPointerException if the artifact does not have a file
   */
  public ClassPathEntry(Artifact artifact) {
    this(artifact.getFile().toPath());
    this.artifact = artifact;
  }

  /** Returns the path to JAR file. */
  Path getJar() {
    return jar;
  }

  /**
   * Returns the Maven artifact associated with the JAR file, or null 
   * if the JAR file does not have Maven coordinates.
   */
  Artifact getArtifact() {
    return artifact;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ClassPathEntry that = (ClassPathEntry) other;
    return Objects.equals(jar, that.jar) && Objects.equals(artifact, that.artifact);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jar, artifact);
  }

  @Override
  public String toString() {
    if (artifact != null) {
      // Group ID, artifact ID and version. No extension such as "jar" or "tar.gz", because Linkage
      // Checker uses only JAR artifacts.
      return Artifacts.toCoordinates(artifact);
    } else {
      return jar.toString();
    }
  }

  /**
   * Reads a list of class file names in {@link #jar} as in {@link JavaClass#getFileName()} and
   * stores them to {@link #classFileNames}. This class file name is usually a fully qualified class
   * name. However a class file name may have a framework-specific prefix. Example: {@code
   * BOOT-INF.classes.com.google.Foo}.
   */
  private void readClassFileNames() throws IOException {
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      ImmutableSet.Builder<String> classNames = ImmutableSet.builder();
      
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.endsWith(".class")) {
          String className = name.replace('/', '.').substring(0, name.length() - 6);
          classNames.add(className);
        }
      }
      this.classFileNames = classNames.build();
    }
  }

  /**
   * Returns class file names in {@link #jar} as in {@link JavaClass#getFileName()}. This class file
   * name is usually a fully qualified class name. However a class file name may have a
   * framework-specific prefix. Example: {@code BOOT-INF.classes.com.google.Foo}.
   *
   * @throws IOException if the jar file can't be read
   */
  public synchronized ImmutableSet<String> getClassFileNames() throws IOException {
    if (classFileNames == null) {
      readClassFileNames();
    }
    return classFileNames;
  }
}
