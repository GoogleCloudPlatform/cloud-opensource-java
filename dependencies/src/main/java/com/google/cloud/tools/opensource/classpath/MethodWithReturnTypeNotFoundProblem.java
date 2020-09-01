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

import javax.annotation.Nullable;

/**
 * The {@code sourceClass} references the {@code methodSymbol}, but the {@code
 * methodSymbol.getClassBinaryName} has the method with different return type.
 */
class MethodWithReturnTypeNotFoundProblem extends LinkageProblem {
  public MethodWithReturnTypeNotFoundProblem(
      ClassFile sourceClass, @Nullable ClassFile targetClass, MethodSymbol methodSymbol) {
    super("is not found (return type does not match)", sourceClass, methodSymbol, targetClass);
  }
}