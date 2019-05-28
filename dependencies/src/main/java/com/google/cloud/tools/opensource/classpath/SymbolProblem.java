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

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A missing or incompatible symbol. This constitutes the cause of a linkage error (without the
 * source class).
 *
 * @see <a
 *     href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#linkage-error">
 *     Java Dependency Glossary: Linkage Error</a>
 */
public final class SymbolProblem {

  private final ErrorType errorType;
  private final Symbol symbol;
  private final ClassFile containingClass;

  public SymbolProblem(Symbol symbol, ErrorType errorType, @Nullable ClassFile containingClass) {
    this.symbol = checkNotNull(symbol);
    this.errorType = checkNotNull(errorType);
    this.containingClass = containingClass;
  }

  /** Returns the errorType why the symbol was not resolved. */
  ErrorType getErrorType() {
    return errorType;
  }

  /** Returns the target symbol that was not resolved. */
  Symbol getSymbol() {
    return symbol;
  }

  /**
   * Returns the referenced class that contains the symbol. Null when the target class is not found
   * in the class path (this is the case if the errorType is {@code CLASS_NOT_FOUND} for top-level
   * classes).
   *
   * <p>In case of a nested class is missing while its outer class is found in the class path, this
   * method returns the outer class.
   */
  @Nullable
  ClassFile getContainingClass() {
    return containingClass;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    SymbolProblem that = (SymbolProblem) other;
    return errorType == that.errorType
        && symbol.equals(that.symbol)
        && Objects.equals(containingClass, that.containingClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(errorType, symbol, containingClass);
  }

  @Override
  public final String toString() {
    StringBuilder builder = new StringBuilder(symbol.toString());
    switch (getErrorType()) {
      case CLASS_NOT_FOUND:
        builder.append(" is not found");
        break;
      case INACCESSIBLE_CLASS:
        builder.append(" is not accessible class");
        break;
      case INCOMPATIBLE_CLASS_CHANGE:
        builder.append(" has changed incompatibly");
        break;
      case SYMBOL_NOT_FOUND:
        builder.append(" is not found in the class");
        break;
      case INACCESSIBLE_MEMBER:
        builder.append(" is not accessible");
        break;
    }
    return builder.toString();
  }
}
