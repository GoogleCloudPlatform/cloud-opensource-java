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

/** Matcher on method symbols. */
class MethodMatcher implements LinkageProblemTargetMatcher {

  private final String className;
  private final String methodName;

  MethodMatcher(String className, String methodName) {
    this.className = checkNotNull(className);
    this.methodName = checkNotNull(methodName);
  }

  /** Returns true if {@code symbol} has {@link #methodName} of {@link #className}. */
  @Override
  public boolean match(Symbol symbol) {
    if (symbol instanceof MethodSymbol) {
      MethodSymbol methodSymbol = (MethodSymbol) symbol;
      return methodSymbol.getClassBinaryName().equals(className)
          && methodSymbol.getName().equals(methodName);
    }
    return false;
  }
}
