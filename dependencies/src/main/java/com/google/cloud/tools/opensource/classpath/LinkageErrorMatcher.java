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

/**
 * Matcher for linkage errors. A linkage error has source class file and target symbol. This matcher
 * holds at least one matcher for them.
 */
class LinkageErrorMatcher implements SymbolProblemMatcher {

  private SourceMatcher sourceMatcher;

  private TargetMatcher targetMatcher;

  void setSourceMatcher(SourceMatcher sourceMatcher) {
    this.sourceMatcher = sourceMatcher;
  }

  void setTargetMatcher(TargetMatcher targetMatcher) {
    this.targetMatcher = targetMatcher;
  }

  /**
   * Returns true if all of non-null matchers of {@link #sourceMatcher} and {@link #targetMatcher}
   * match.
   */
  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    if (sourceMatcher != null && !sourceMatcher.match(problem, sourceClass)) {
      return false;
    }
    if (targetMatcher != null && !targetMatcher.match(problem, sourceClass)) {
      return false;
    }
    return true;
  }
}
