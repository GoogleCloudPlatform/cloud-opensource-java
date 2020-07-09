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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A linkage error.
 *
 * @see <a href="https://jlbp.dev/glossary.html#linkage-error">Java Dependency Glossary: Linkage
 *     Error</a>
 */
public final class LinkageProblem {

  private final ErrorType errorType;
  private final Symbol symbol;
  private final ClassFile containingClass;
  private final ClassFile sourceClass;

  @VisibleForTesting
  public LinkageProblem(
      Symbol symbol,
      ErrorType errorType,
      @Nullable ClassFile containingClass,
      ClassFile sourceClass) {
    Preconditions.checkNotNull(symbol);

    // After finding symbol problem, there is no need to have SuperClassSymbol over ClassSymbol.
    this.symbol =
        symbol instanceof SuperClassSymbol ? new ClassSymbol(symbol.getClassBinaryName()) : symbol;
    this.errorType = Preconditions.checkNotNull(errorType);
    this.containingClass = containingClass;
    this.sourceClass = sourceClass;
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
   * Returns the class that is expected to contain the symbol. If the symbol is a method
   * or a field, then this is the class where the symbol was expected to be found.
   * If the symbol is an inner class, this is the outer class that was expected 
   * to contain the inner class. If the symbol is an outer class, this is null.
   */
  @Nullable
  public ClassFile getContainingClass() {
    return containingClass;
  }

  /** Returns the source of the invalid reference which this linkage error represents. */
  public ClassFile getSourceClass() {
    return sourceClass;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    LinkageProblem that = (LinkageProblem) other;
    return errorType == that.errorType
        && symbol.equals(that.symbol)
        && Objects.equals(containingClass, that.containingClass)
        && Objects.equals(sourceClass, that.sourceClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(errorType, symbol, containingClass, sourceClass);
  }

  @Override
  public final String toString() {
    String jarInfo =
        containingClass != null ? String.format("(%s) ", containingClass.getClassPathEntry()) : "";
    return jarInfo + getErrorType().getMessage(symbol.toString());
  }

  public static String formatLinkageProblems(Set<LinkageProblem> linkageProblems) {
    StringBuilder output = new StringBuilder();

    // Group by the symbols
    ImmutableListMultimap<Symbol, LinkageProblem> groupBySymbols =
        Multimaps.index(linkageProblems, problem -> problem.getSymbol());

    groupBySymbols
        .asMap()
        .forEach(
            (symbol, problems) -> {
              int referenceCount = problems.size();
              output.append(
                  String.format(
                      "%s;\n  referenced by %d class file%s\n",
                      symbol, referenceCount, referenceCount > 1 ? "s" : ""));
              problems.forEach(
                  problem -> {
                    ClassFile sourceClassFile = problem.getSourceClass();
                    output.append("    " + sourceClassFile.getBinaryName());
                    output.append(" (" + sourceClassFile.getClassPathEntry() + ")\n");
                  });
            });

    return output.toString();
  }
}
