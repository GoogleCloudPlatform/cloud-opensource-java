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

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Linkage error caused by a symbol reference that cannot be resolved. This class does not carry the
 * source class of the reference.
 *
 * @see <a href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#linkage-error">
 * Java Dependency Glossary: Linkage Error</a>
 */
class SymbolProblem {

  private final Reason reason;
  private final Symbol symbol;
  private final ClassInJar targetClass;

  SymbolProblem(Symbol symbol, Reason reason, @Nullable ClassInJar targetClass) {
    this.reason = reason;
    this.symbol = symbol;
    this.targetClass = targetClass;
  }

  /** Returns the reason why the symbol was not resolved. */
  Reason getReason() {
    return reason;
  }

  /** Returns the target symbol that was not resolved. */
  Symbol getSymbol() {
    return symbol;
  }

  /**
   * Returns an {@code Optional} describing the referenced class of the linkage conflict. An empty
   * {@code Optional} when the target class is not found in the class path (this is the case if
   * reason is {@code CLASS_NOT_FOUND}).
   *
   * <p>In case of an inner class is missing while its outer class is found in the class path, this
   * method returns the outer class.
   */
  Optional<ClassInJar> getTargetClass() {
    if (targetClass == null) {
      return Optional.empty();
    }
    return Optional.of(targetClass);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SymbolProblem that = (SymbolProblem) o;
    return reason == that.reason
        && Objects.equals(symbol, that.symbol)
        && Objects.equals(targetClass, that.targetClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reason, symbol, targetClass);
  }

  /** The kind of linkage error against a symbol reference. */
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
     * The target member (method or field) is inaccessible to the source.
     *
     * <p>If the source is in a different package, the member is not public. If the source is in the
     * same package, the class is private. If the source is a subclass of the target class, the
     * member is not protected or public.
     */
    INACCESSIBLE_MEMBER,

    /** For a method or field reference, the symbol is not found in the target class. */
    SYMBOL_NOT_FOUND
  }
}
