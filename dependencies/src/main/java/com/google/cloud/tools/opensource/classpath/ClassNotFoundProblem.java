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

/** The {@code classSymbol}, referenced by {@code sourceClass} is not found in the class path. */
public final class ClassNotFoundProblem extends LinkageProblem {
  private final ClassSymbol classSymbol;

  public ClassNotFoundProblem(ClassFile sourceClass, ClassSymbol classSymbol) {
    super("is not found", sourceClass, classSymbol);
    this.classSymbol = classSymbol;
  }

  private ClassNotFoundProblem(
      ClassFile sourceClass, ClassSymbol classSymbol, LinkageProblemCause cause) {
    super("is not found", sourceClass, classSymbol, cause);
    this.classSymbol = classSymbol;
  }

  @Override
  LinkageProblem withCause(LinkageProblemCause cause) {
    return new ClassNotFoundProblem(getSourceClass(), classSymbol, cause);
  }
}
