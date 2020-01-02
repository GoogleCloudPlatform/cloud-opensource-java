package com.google.cloud.tools.opensource.serializable;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;

public final class ClassFile {

  private final String coordinates;
  private final String className;

  @JsonCreator
  public ClassFile(@JsonProperty("coordinates") String coordinates, @JsonProperty("className") String className) {
    this.coordinates = checkNotNull(coordinates);
    this.className = checkNotNull(className);
  }


  /** Returns class name (binary name as in {@link Symbol#getClassName()}) */
  public String getClassName() {
    return className;
  }

  public String getCoordinates() {
    return coordinates;
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
    return Objects.equals(coordinates, that.coordinates) && Objects.equals(className, that.className);
  }

  @Override
  public int hashCode() {
    return Objects.hash(coordinates, className);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add(coordinates, coordinates)
        .add("className", className)
        .toString();
  }

}
