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
import java.net.URL;
import javax.annotation.Nullable;

/**
 * Static linkage error caused by a symbol reference. The symbol reference is one of the three
 * types: {@link ClassSymbolReference}, {@link MethodSymbolReference}, and {@link
 * FieldSymbolReference}.
 *
 * @param <T> type of symbol reference that caused the linkage error
 * @see <a
 *     href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#static-linkage-error">
 *     Java Dependency Glossary: Static linkage error</a>
 */
@AutoValue
abstract class LinkageErrorOnReference<T extends SymbolReference> {

  /** Returns the symbol reference on which this linkage error occurred. */
  abstract T getReference();

  /**
   * Returns the location of the target class in the symbol reference; null if the target class is
   * not found in the class path or the source location is unavailable.
   */
  @Nullable
  abstract URL getTargetClassLocation();

  /** Returns the reason why the symbol reference is marked as a linkage error. */
  abstract Reason getReason();

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
    return builder.toString();
  }

  /** Returns a linkage error caused by {@link Reason#CLASS_NOT_FOUND}. */
  static <U extends SymbolReference> LinkageErrorOnReference<U> errorMissingTargetClass(
      U reference) {
    return builderFor(reference).setReason(Reason.CLASS_NOT_FOUND).build();
  }

  /** Returns a linkage error caused by {@link Reason#SYMBOL_NOT_FOUND}. */
  static <U extends SymbolReference> LinkageErrorOnReference<U> errorMissingMember(
      U reference, URL targetClassLocation) {
    return builderFor(reference)
        .setReason(Reason.SYMBOL_NOT_FOUND)
        .setTargetClassLocation(targetClassLocation)
        .build();
  }

  /** Returns a linkage error caused by {@link Reason#INACCESSIBLE}. */
  static <U extends SymbolReference> LinkageErrorOnReference<U> errorInvalidModifier(
      U reference, URL targetClassLocation) {
    return builderFor(reference)
        .setReason(Reason.INACCESSIBLE)
        .setTargetClassLocation(targetClassLocation)
        .build();
  }

  /** Returns {@code Builder} for a linkage error that occurred at the symbol reference. */
  private static <U extends SymbolReference> Builder<U> builderFor(U reference) {
    // This method gives type-safety compared with normal builder() method.
    Builder<U> builder = new AutoValue_LinkageErrorOnReference.Builder<>();
    builder.setReference(reference);
    return builder;
  }

  @AutoValue.Builder
  abstract static class Builder<T extends SymbolReference> {

    abstract LinkageErrorOnReference.Builder<T> setTargetClassLocation(URL targetClassLocation);

    abstract LinkageErrorOnReference.Builder<T> setReason(Reason reason);

    abstract LinkageErrorOnReference.Builder<T> setReference(T reference);

    abstract LinkageErrorOnReference<T> build();
  }

  /** Reason to distinguish the cause of a static linkage error against a symbol reference. */
  enum Reason {
    /** The target class of the symbol reference is not found in the class path. */
    CLASS_NOT_FOUND,

    /**
     * The symbol is inaccessible to the source.
     *
     * <p>If the source is in a different package, the symbol or one of its enclosing types is not
     * public. If the source is in the same package, the symbol or one of its enclosing types is
     * private.
     */
    // TODO(#293): enrich javadoc for linkage error on inaccessible method and field
    INACCESSIBLE,

    /**
     * For a method or field reference, the symbol is not found in the target class in the class
     * path.
     */
    SYMBOL_NOT_FOUND
  }
}
