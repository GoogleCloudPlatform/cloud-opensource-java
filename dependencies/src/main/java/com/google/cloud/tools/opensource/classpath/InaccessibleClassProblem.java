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
 * The {@code classSymbol} is inaccessible to the {@code sourceClass} as per {@code sourceClass}'s
 * definition of the class symbol.
 *
 * <p>If the source class is in a different package than the target class, the class or one of its
 * enclosing types is not public. If the source class is in the same package, the class or one of
 * its enclosing types is private.
 */
final class InaccessibleClassProblem extends IncompatibleLinkageProblem {

  InaccessibleClassProblem(ClassFile sourceClass, ClassFile targetClass, Symbol classSymbol) {
    this(sourceClass, targetClass, classSymbol, null);
  }

  private InaccessibleClassProblem(
      ClassFile sourceClass, ClassFile targetClass, Symbol classSymbol, LinkageProblemCause cause) {
    super("is not accessible", sourceClass, targetClass, classSymbol, cause);
  }

  @Override
  LinkageProblem withCause(LinkageProblemCause cause) {
    return new InaccessibleClassProblem(getSourceClass(), getTargetClass(), getSymbol(), cause);
  }
}
