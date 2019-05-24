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
 * Linkage error caused by a symbol reference that cannot be resolved.
 *
 * @param <T> symbol reference that caused the linkage error
 * @see <a
 *     href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#linkage-error">
 *     Java Dependency Glossary: Linkage Error</a>
 */
@AutoValue
abstract class SymbolNotResolvable<T extends SymbolReference> {

  /** Returns the symbol reference that could not be resolved. */
  abstract T getReference();

  /**
   * Returns the path to the class where the target of the reference was expected to be found.
   * This is null if the target class is not found in the class path or the source
   * location is unavailable.
   */
  @Nullable
  abstract Path getTargetClassLocation();

  /** Returns the reason why the symbol reference is marked as a linkage error. */
  abstract ErrorType getReason();

  /**
   * Returns true if the source class of the reference is reachable from entry point classes.
   *
   * @see <a
   *     href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#class-reference-graph">
   *     Java Dependency Glossary: Class Reference Graph</a>
   */
  abstract boolean isReachable();

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
    builder.append(isReachable());
    return builder.toString();
  }

  /** Returns a SymbolNotFound caused by {@link ErrorType#CLASS_NOT_FOUND}. */
  static <U extends SymbolReference> SymbolNotResolvable<U> errorMissingTargetClass(
      U reference, boolean isReachable) {
    return builderFor(reference)
        .setReason(ErrorType.CLASS_NOT_FOUND)
        .setReachable(isReachable)
        .build();
  }

  /** Returns a SymbolNotFound caused by {@link ErrorType#INCOMPATIBLE_CLASS_CHANGE}. */
  static <U extends SymbolReference> SymbolNotResolvable<U> errorIncompatibleClassChange(
      U reference, Path targetClassLocation, boolean isReachable) {
    return builderFor(reference)
        .setReason(ErrorType.INCOMPATIBLE_CLASS_CHANGE)
        .setTargetClassLocation(targetClassLocation)
        .setReachable(isReachable)
        .build();
  }

  /** Returns a SymbolNotFound caused by {@link ErrorType#SYMBOL_NOT_FOUND}. */
  static <U extends SymbolReference> SymbolNotResolvable<U> errorMissingMember(
      U reference, Path targetClassLocation, boolean isReachable) {
    return builderFor(reference)
        .setReason(ErrorType.SYMBOL_NOT_FOUND)
        .setTargetClassLocation(targetClassLocation)
        .setReachable(isReachable)
        .build();
  }

  /** Returns a SymbolNotFound caused by {@link ErrorType#INACCESSIBLE_CLASS}. */
  static <U extends SymbolReference> SymbolNotResolvable<U> errorInaccessibleClass(
      U reference, Path targetClassLocation, boolean isReachable) {
    return builderFor(reference)
        .setReason(ErrorType.INACCESSIBLE_CLASS)
        .setTargetClassLocation(targetClassLocation)
        .setReachable(isReachable)
        .build();
  }

  /** Returns a SymbolNotFound caused by {@link ErrorType#INACCESSIBLE_MEMBER}. */
  static <U extends SymbolReference> SymbolNotResolvable<U> errorInaccessibleMember(
      U reference, Path targetClassLocation, boolean isReachable) {
    return builderFor(reference)
        .setReason(ErrorType.INACCESSIBLE_MEMBER)
        .setTargetClassLocation(targetClassLocation)
        .setReachable(isReachable)
        .build();
  }

  /**
   * Returns a {@code SymbolNotFound.Builder} for a linkage error that occurred at the symbol
   * reference.
   */
  private static <U extends SymbolReference> Builder<U> builderFor(U reference) {
    // This method gives type-safety compared with normal builder() method.
    Builder<U> builder = new AutoValue_SymbolNotResolvable.Builder<>();
    builder.setReference(reference);
    return builder;
  }

  @AutoValue.Builder
  abstract static class Builder<T extends SymbolReference> {

    abstract SymbolNotResolvable.Builder<T> setTargetClassLocation(Path targetClassLocation);

    abstract SymbolNotResolvable.Builder<T> setReason(ErrorType reason);

    abstract SymbolNotResolvable.Builder<T> setReference(T reference);

    abstract SymbolNotResolvable.Builder<T> setReachable(boolean reachable);

    abstract SymbolNotResolvable<T> build();
  }

  static <U extends SymbolReference> SymbolNotResolvable<U> fromSymbolProblem(
      U symbolReference,
      SymbolProblem symbolProblem,
      Path targetClassLocation,
      boolean isReachable) {
    return builderFor(symbolReference)
        .setReason(symbolProblem.getErrorType())
        .setTargetClassLocation(targetClassLocation)
        .setReachable(isReachable)
        .build();
  }
}
