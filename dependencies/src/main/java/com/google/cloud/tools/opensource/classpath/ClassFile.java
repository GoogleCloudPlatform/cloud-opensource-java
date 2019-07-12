package com.google.cloud.tools.opensource.classpath;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A locator for a compiled class file of {@code className} in {@code jar} to uniquely locate the
 * class implementation in a class path.
 */
public final class ClassFile {
  private final Path jar;
  private final String className;

  public ClassFile(Path jar, String className) {
    this.jar = checkNotNull(jar);
    this.className = checkNotNull(className);
  }

  /** Returns the path to the JAR file containing the class. */
  public Path getJar() {
    return jar;
  }

  /** Returns class name (binary name as in {@link Symbol#getClassName()}) */
  public String getClassName() {
    return className;
  }

  /**
   * Returns {@link ClassFile} with top level class if this class is an inner class by checking "$"
   * character; otherwise the instance itself.
   */
  public ClassFile topLevelClassFile() {
    return className.contains("$") ? new ClassFile(jar, className.split("\\$")[0]) : this;
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
    return Objects.equals(jar, that.jar) && Objects.equals(className, that.className);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jar, className);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("jar", jar)
        .add("className", className)
        .toString();
  }
}
