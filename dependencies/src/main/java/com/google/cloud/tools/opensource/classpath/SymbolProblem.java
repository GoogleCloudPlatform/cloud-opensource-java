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

import com.google.common.collect.ImmutableSetMultimap;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A missing or incompatible symbol that causes a linkage error.
 *
 * @see <a href="https://jlbp.dev/glossary.html#linkage-error?">
 *     Java Dependency Glossary: Linkage Error</a>
 */
public final class SymbolProblem {

  private final ErrorType errorType;
  private final Symbol symbol;
  private final ClassFile containingClass;

  public SymbolProblem(Symbol symbol, ErrorType errorType, @Nullable ClassFile containingClass) {
    checkNotNull(symbol);

    // After finding symbol problem, there is no need to have SuperClassSymbol over ClassSymbol.
    this.symbol =
        symbol instanceof SuperClassSymbol ? new ClassSymbol(symbol.getClassName()) : symbol;
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
   * Returns the class that references the symbol. If the symbol is a method or a field,
   * then this is the class where the symbol was expected to be found.
   * If the symbol is an inner class, this is the outer class that was expected 
   * to contain the inner class. If the symbol is an outer class, this is null.
   */
  @Nullable
  public ClassFile getContainingClass() {
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
    String jarInfo =
        containingClass != null
            ? String.format("(%s) ", containingClass.getJar().getFileName())
            : "";
    return jarInfo + getErrorType().getMessage(symbol.toString());
  }

  public static String formatSymbolProblems(
      ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems) {
    StringBuilder output = new StringBuilder();

    symbolProblems
        .asMap()
        .forEach(
            (problem, classFiles) -> {
              int referenceCount = classFiles.size();
              output.append(
                  String.format(
                      "%s;\n  referenced by %d class file%s\n",
                      problem, referenceCount, referenceCount > 1 ? "s" : ""));
              classFiles.forEach(
                  classFile -> {
                    output.append("    " + classFile.getClassName());
                    output.append(" (" + classFile.getJar().getFileName() + ")\n");
                  });
            });

    return output.toString();
  }
}
