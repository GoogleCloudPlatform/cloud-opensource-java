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

import javax.annotation.Nullable;

/** Matcher for linkage errors. A linkage error has a target symbol and a source class file. */
class LinkageErrorMatcher implements LinkageProblemMatcher {

  /** Matcher for the source class of the linkage error. Null if no Source element. */
  @Nullable private SourceMatcher sourceMatcher;

  /** Matcher for the target symbol of the linkage error. Null if no Target element. */
  @Nullable private TargetMatcher targetMatcher;

  void setSourceMatcher(SourceMatcher sourceMatcher) {
    this.sourceMatcher = checkNotNull(sourceMatcher);
  }

  void setTargetMatcher(TargetMatcher targetMatcher) {
    this.targetMatcher = checkNotNull(targetMatcher);
  }

  /**
   * Returns true if {@link #sourceMatcher} matches if present and {@link #targetMatcher} matches if
   * present.
   */
  @Override
  public boolean match(LinkageProblem problem) {
    if (sourceMatcher != null && !sourceMatcher.match(problem)) {
      return false;
    }
    if (targetMatcher != null && !targetMatcher.match(problem)) {
      return false;
    }
    return true;
  }
}
