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

/** Matcher on the source class file of linkage errors. */
class SourceMatcher implements SymbolProblemMatcher {

  private SymbolProblemSourceMatcher matcher;

  @Override
  public void addChild(SymbolProblemTargetMatcher child) {
    this.matcher = (SymbolProblemSourceMatcher) child;
  }

  @Override
  public boolean match(LinkageProblem problem) {
    return matcher.match(problem.getSourceClass());
  }
}
