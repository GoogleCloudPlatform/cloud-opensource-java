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

import java.util.Objects;

/**
 * A {@link LinkageProblem} caused by an invalid reference when both {@code sourceClass} and {@code
 * targetClass} are present in the class path.
 */
public abstract class IncompatibleLinkageProblem extends LinkageProblem {
  private final ClassFile targetClass;

  IncompatibleLinkageProblem(
      String symbolProblemMessage, ClassFile sourceClass, ClassFile targetClass, Symbol symbol) {
    super(symbolProblemMessage, sourceClass, symbol);
    this.targetClass = targetClass;
  }

  @Override
  public String formatSymbolProblem() {
    String jarInfo = "(" + targetClass.getClassPathEntry() + ") ";
    return jarInfo + super.formatSymbolProblem();
  }

  /**
   * Returns the class that is expected to contain the symbol. If the symbol is a method or a field,
   * then this is the class where the symbol was expected to be found. If the symbol is an inner
   * class, this is the outer class that was expected to contain the inner class.
   */
  public ClassFile getTargetClass() {
    return targetClass;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    IncompatibleLinkageProblem that = (IncompatibleLinkageProblem) other;
    return super.equals(other) && Objects.equals(targetClass, that.targetClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), targetClass);
  }
}
