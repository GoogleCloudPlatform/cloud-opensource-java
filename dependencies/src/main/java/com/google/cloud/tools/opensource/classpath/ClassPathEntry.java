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
import org.eclipse.aether.artifact.Artifact;

/** An entry in a class path. */
public final class ClassPathEntry {

  private Path jar;
  private Artifact artifact;
  private ImmutableSet<String> fileNames;

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
   * Returns the Maven artifact associated with the JAR file, or null if the JAR file does not have
   * Maven coordinates.
   */
  public Artifact getArtifact() {
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
   * Populates {@link #fileNames} through the classes in {@link #jar}. These file names are usually
   * fully qualified class names. However a class file name may have a framework-specific prefix.
   * Example: {@code BOOT-INF.classes.com.google.Foo}.
   */
  private void readFileNames() throws IOException {
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.endsWith(".class")) {
          String className = name.replace('/', '.').substring(0, name.length() - 6);
          builder.add(className);
        }
      }
      this.fileNames = builder.build();
    }
  }

  /**
   * Returns the names of the .class files in this entry's jar file.
   * A file name is the name of the .class file in the JAR file, without the 
   * suffix {@code .class} and after converting each / to a period.
   * 
   * @throws IOException if the jar file can't be read
   */
  public synchronized ImmutableSet<String> getFileNames() throws IOException {
    if (fileNames == null) {
      readFileNames();
    }
    return fileNames;
  }
}
