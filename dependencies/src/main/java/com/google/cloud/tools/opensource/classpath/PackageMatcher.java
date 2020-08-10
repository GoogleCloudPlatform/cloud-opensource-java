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

/**
 * Matcher on the classes of a package and its subpackages.
 *
 * <p>For example, {@code PackageMatcher("com.google")} matches {@code com.google.Foo} class as well
 * as {@code com.google.cloud.Bar}.
 */
class PackageMatcher implements LinkageProblemTargetMatcher, LinkageProblemSourceMatcher {

  private final String packageName;

  PackageMatcher(String packageName) {
    this.packageName = checkNotNull(packageName);
  }

  @Override
  public boolean match(Symbol problem) {
    return problem.getClassBinaryName().startsWith(packageName + ".");
  }

  @Override
  public boolean match(ClassFile source) {
    return source.getBinaryName().startsWith(packageName);
  }
}
