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

/** Matcher on field symbols. */
class FieldMatcher implements SymbolProblemTargetMatcher {

  private final String className;
  private final String fieldName;

  FieldMatcher(String className, String fieldName) {
    this.className = checkNotNull(className);
    this.fieldName = checkNotNull(fieldName);
  }

  /** Returns true if {@code symbol} is {@link #fieldName} of {@link #className}. */
  @Override
  public boolean match(SymbolProblem symbolProblem) {
    Symbol symbol = symbolProblem.getSymbol();
    if (symbol instanceof FieldSymbol) {
      FieldSymbol fieldSymbol = (FieldSymbol) symbol;
      return fieldSymbol.getClassBinaryName().equals(className)
          && fieldSymbol.getName().equals(fieldName);
    }
    return false;
  }
}
