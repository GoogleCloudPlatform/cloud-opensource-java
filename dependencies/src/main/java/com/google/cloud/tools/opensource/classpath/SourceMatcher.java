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

import java.util.ArrayList;
import java.util.List;

/** Matcher on the source class file of linkage errors. */
class SourceMatcher implements SymbolProblemMatcher {

  private List<SymbolProblemSourceMatcher> matchers = new ArrayList<>();

  void addMatcher(SymbolProblemSourceMatcher matcher) {
    matchers.add(matcher);
  }

  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    // If one of child matches, return true
    for (SymbolProblemSourceMatcher matcher : matchers) {
      if (matcher.match(sourceClass)) {
        return true;
      }
    }
    return false;
  }
}
