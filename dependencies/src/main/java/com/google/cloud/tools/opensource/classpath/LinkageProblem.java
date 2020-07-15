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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A linkage error describing an invalid reference from {@code sourceClass} to {@code symbol}.
 *
 * @see <a href="https://jlbp.dev/glossary.html#linkage-error">Java Dependency Glossary: Linkage
 *     Error</a>
 */
public abstract class LinkageProblem {

  private final Symbol symbol;
  private final ClassFile sourceClass;
  private final String symbolProblemMessage;
  private LinkageProblemCause cause;

  /**
   * A linkage error describing an invalid reference.
   *
   * @param symbolProblemMessage human-friendly description of this linkage error. Used in
   *     conjunction with {@code symbol}, this value explains why we consider the reference to
   *     {@code symbol} as a linkage error.
   * @param sourceClass the source of the invalid reference.
   * @param symbol the target of the invalid reference
   */
  LinkageProblem(String symbolProblemMessage, ClassFile sourceClass, Symbol symbol) {
    this.symbolProblemMessage = Preconditions.checkNotNull(symbolProblemMessage);
    Preconditions.checkNotNull(symbol);

    // After finding symbol problem, there is no need to have SuperClassSymbol over ClassSymbol.
    this.symbol =
        symbol instanceof SuperClassSymbol ? new ClassSymbol(symbol.getClassBinaryName()) : symbol;
    this.sourceClass = Preconditions.checkNotNull(sourceClass);
  }

  /** Returns the target symbol that was not resolved. */
  public Symbol getSymbol() {
    return symbol;
  }

  /** Returns the source of the invalid reference which this linkage error represents. */
  public ClassFile getSourceClass() {
    return sourceClass;
  }

  void setCause(LinkageProblemCause cause) {
    this.cause = checkNotNull(cause);
  }

  LinkageProblemCause getCause() {
    return cause;
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
    return symbol.equals(that.symbol) && Objects.equals(sourceClass, that.sourceClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(symbol, sourceClass);
  }

  @Override
  public final String toString() {
    return formatSymbolProblem() + " referenced by " + sourceClass;
  }

  /**
   * Returns the description of the problem on the {@code symbol}. This description does not include
   * the {@code sourceClass}. This value is useful when grouping {@link LinkageProblem}s by their
   * {@code symbol}s.
   */
  public String formatSymbolProblem() {
    return symbol + " " + symbolProblemMessage;
  }

  /** Returns mapping from symbol problem description to the names of the source classes. */
  public static ImmutableMap<String, ImmutableSet<String>> groupBySymbolProblem(
      Iterable<LinkageProblem> linkageProblems) {
    ImmutableListMultimap<String, LinkageProblem> groupedMultimap =
        Multimaps.index(linkageProblems, problem -> problem.formatSymbolProblem());

    ListMultimap<String, String> symbolProblemToSourceClasses =
        Multimaps.transformValues(
            groupedMultimap, problem -> problem.getSourceClass().getBinaryName());
    Map<String, ImmutableSet<String>> valueTransformed =
        Maps.transformValues(symbolProblemToSourceClasses.asMap(), ImmutableSet::copyOf);
    return ImmutableMap.copyOf(valueTransformed);
  }

  /** Returns the formatted {@code linkageProblems} by grouping them by the {@code symbol}s. */
  public static String formatLinkageProblems(Set<LinkageProblem> linkageProblems) {
    StringBuilder output = new StringBuilder();

    // Group by the symbols
    ImmutableListMultimap<Symbol, LinkageProblem> groupBySymbols =
        Multimaps.index(linkageProblems, problem -> problem.getSymbol());

    groupBySymbols
        .asMap()
        .forEach(
            (symbol, problems) -> {
              // problems all have the same symbol problem
              LinkageProblem firstProblem = Iterables.getFirst(problems, null);
              int referenceCount = problems.size();
              output.append(
                  String.format(
                      "%s;\n  referenced by %d class file%s\n",
                      firstProblem.formatSymbolProblem(),
                      referenceCount,
                      referenceCount > 1 ? "s" : ""));
              ImmutableSet.Builder<LinkageProblemCause> causesBuilder = ImmutableSet.builder();
              problems.forEach(
                  problem -> {
                    ClassFile sourceClassFile = problem.getSourceClass();
                    output.append("    " + sourceClassFile.getBinaryName());
                    output.append(" (" + sourceClassFile.getClassPathEntry() + ")\n");

                    LinkageProblemCause cause = problem.getCause();
                    if (cause != null) {
                      causesBuilder.add(cause);
                    }
                  });
              ImmutableSet<LinkageProblemCause> causes = causesBuilder.build();
              if (!causes.isEmpty()) {
                output.append("  Cause:\n");
                for (LinkageProblemCause cause : causes) {
                  output.append("    " + cause + "\n");
                }
              }
            });

    return output.toString();
  }
}
