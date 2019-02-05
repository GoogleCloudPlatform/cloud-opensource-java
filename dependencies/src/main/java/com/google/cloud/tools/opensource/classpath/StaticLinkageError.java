/*
 * Copyright 2018 Google LLC.
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

import com.google.auto.value.AutoValue;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Static linkage error caused by a symbol reference.
 *
 * @param <T> type of symbol reference that caused the linkage error
 * @see <a
 *     href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#static-linkage-error">
 *     Java Dependency Glossary: Static linkage error</a>
 */
@AutoValue
abstract class StaticLinkageError<T extends SymbolReference> {

  /** Returns the symbol reference on which this linkage error occurred. */
  abstract T getReference();

  /**
   * Returns the location of the target class in the symbol reference; null if the target class is
   * not found in the class path or the source location is unavailable.
   */
  @Nullable
  abstract Path getTargetClassLocation();

  /** Returns the reason why the symbol reference is marked as a linkage error. */
  abstract Reason getReason();

  abstract boolean isSourceClassReachable();

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(getReference());
    builder.append(", reason: " + getReason());
    if (getTargetClassLocation() != null) {
      builder.append(", target class from " + getTargetClassLocation());
    } else {
      builder.append(", target class location not found");
    }
    builder.append(", isReachable: ");
    builder.append(isSourceClassReachable());
    return builder.toString();
  }

  /** Returns a linkage error caused by {@link Reason#CLASS_NOT_FOUND}. */
  static <U extends SymbolReference> StaticLinkageError<U> errorMissingTargetClass(
      U reference, boolean isReachable) {
    return builderFor(reference)
        .setReason(Reason.CLASS_NOT_FOUND)
        .setSourceClassReachable(isReachable)
        .build();
  }

  /** Returns a linkage error caused by {@link Reason#INCOMPATIBLE_CLASS_CHANGE}. */
  static <U extends SymbolReference> StaticLinkageError<U> errorIncompatibleClassChange(
      U reference, Path targetClassLocation, boolean isReachable) {
    return builderFor(reference)
        .setReason(Reason.INCOMPATIBLE_CLASS_CHANGE)
        .setTargetClassLocation(targetClassLocation)
        .setSourceClassReachable(isReachable)
        .build();
  }

  /** Returns a linkage error caused by {@link Reason#SYMBOL_NOT_FOUND}. */
  static <U extends SymbolReference> StaticLinkageError<U> errorMissingMember(
      U reference, Path targetClassLocation, boolean isReachable) {
    return builderFor(reference)
        .setReason(Reason.SYMBOL_NOT_FOUND)
        .setTargetClassLocation(targetClassLocation)
        .setSourceClassReachable(isReachable)
        .build();
  }

  /** Returns a linkage error caused by {@link Reason#INACCESSIBLE_CLASS}. */
  static <U extends SymbolReference> StaticLinkageError<U> errorInaccessibleClass(
      U reference, Path targetClassLocation, boolean isReachable) {
    return builderFor(reference)
        .setReason(Reason.INACCESSIBLE_CLASS)
        .setTargetClassLocation(targetClassLocation)
        .setSourceClassReachable(isReachable)
        .build();
  }

  /** Returns a linkage error caused by {@link Reason#INACCESSIBLE_MEMBER}. */
  static <U extends SymbolReference> StaticLinkageError<U> errorInaccessibleMember(
      U reference, Path targetClassLocation, boolean isReachable) {
    return builderFor(reference)
        .setReason(Reason.INACCESSIBLE_MEMBER)
        .setTargetClassLocation(targetClassLocation)
        .setSourceClassReachable(isReachable)
        .build();
  }

  /** Returns {@code Builder} for a linkage error that occurred at the symbol reference. */
  private static <U extends SymbolReference> Builder<U> builderFor(U reference) {
    // This method gives type-safety compared with normal builder() method.
    Builder<U> builder = new AutoValue_StaticLinkageError.Builder<>();
    builder.setReference(reference);
    return builder;
  }

  @AutoValue.Builder
  abstract static class Builder<T extends SymbolReference> {

    abstract StaticLinkageError.Builder<T> setTargetClassLocation(Path targetClassLocation);

    abstract StaticLinkageError.Builder<T> setReason(Reason reason);

    abstract StaticLinkageError.Builder<T> setReference(T reference);

    abstract StaticLinkageError.Builder<T> setSourceClassReachable(boolean reachable);

    abstract StaticLinkageError<T> build();
  }

  /** Reason to distinguish the cause of a static linkage error against a symbol reference. */
  enum Reason {
    /** The target class of the symbol reference is not found in the class path. */
    CLASS_NOT_FOUND,

    /**
     * The referenced class or interface found in the class path is not compatible with the source.
     */
    INCOMPATIBLE_CLASS_CHANGE,

    /**
     * The target class of the symbol reference is inaccessible to the source.
     *
     * <p>If the source is in a different package, the class or one of its enclosing types is not
     * public. If the source is in the same package, the class or one of its enclosing types is
     * private.
     */
    INACCESSIBLE_CLASS,

    /**
     * The member (method or field) is inaccessible to the source.
     *
     * <p>If the source is in a different package, the member is not public. If the source is in the
     * same package, the class is private. If the source is a subclass of the target class, the
     * member is not protected.
     */
    INACCESSIBLE_MEMBER,

    /**
     * For a method or field reference, the symbol is not found in the target class in the class
     * path.
     */
    SYMBOL_NOT_FOUND
  }
}
