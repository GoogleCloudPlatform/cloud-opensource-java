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
 * The referenced {@code methodSymbol} is not implemented in the {@code sourceClass} but the class
 * is declared to implement the method by {@code targetClass}. Such unimplemented methods manifest
 * as {@link AbstractMethodError}s at runtime.
 */
final class AbstractMethodProblem extends LinkageProblem {
  MethodSymbol methodSymbol;

  AbstractMethodProblem(ClassFile sourceClass, ClassFile targetClass, MethodSymbol methodSymbol) {
    super("is not implemented in the subclass", sourceClass, methodSymbol, targetClass);
    this.methodSymbol = methodSymbol;
  }

  @Override
  public final String toString() {
    ClassFile sourceClass = getSourceClass();
    ClassPathEntry sourceClassPathEntry = sourceClass.getClassPathEntry();
    ClassFile targetClass = getTargetClass();
    return String.format(
        "%s (in %s) does not implement %s, required by %s (in %s)",
        sourceClass.getBinaryName(),
        sourceClassPathEntry,
        methodSymbol.getMethodNameWithSignature(),
        targetClass.getBinaryName(),
        targetClass.getClassPathEntry());
  }
}
