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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;
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
   * Returns a list of class file names in {@link #jar} as in {@link JavaClass#getFileName()}. This
   * class file name is usually a fully qualified class name. However a class file name may have a
   * framework-specific prefix. Example: {@code BOOT-INF.classes.com.google.Foo}.
   */
  private void readClassFileNames() throws IOException {
    URL jarUrl = jar.toUri().toURL();
    // Setting parent as null because we don't want other classes than this jar file
    URLClassLoader classLoaderFromJar = new URLClassLoader(new URL[] {jarUrl}, null);

    // Leveraging Google Guava reflection as BCEL doesn't have API to list classes in a jar file
    com.google.common.reflect.ClassPath classPath =
        com.google.common.reflect.ClassPath.from(classLoaderFromJar);

    classFileNames = classPath.getAllClasses().stream().map(ClassInfo::getName).collect(toImmutableSet());
  }
  
  /**
   * Returns the fully qualified names of the classes in this entry's jar file.
   * 
   * @throws IOException if the jar file can't be read
   */
  public synchronized ImmutableSet<String> getClassNames() throws IOException {
    if (classFileNames == null) {
      readClassFileNames();
    }
    return classFileNames;
  }
}
