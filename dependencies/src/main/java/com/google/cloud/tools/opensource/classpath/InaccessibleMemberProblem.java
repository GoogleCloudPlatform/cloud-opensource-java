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
 * The {@code symbol} (method or field) defined in {@code targetClass} is inaccessible to the {@code
 * sourceClass}.
 *
 * <p>If the source class is in a different package than the target class, the member is not public.
 * If the source is in the same package, the member is private. If the source is a subclass of the
 * target class, the member is not protected or public.
 */
final class InaccessibleMemberProblem extends IncompatibleLinkageProblem {
  InaccessibleMemberProblem(ClassFile sourceClass, ClassFile targetClass, Symbol symbol) {
    this(sourceClass, targetClass, symbol, null);
  }

  private InaccessibleMemberProblem(
      ClassFile sourceClass, ClassFile targetClass, Symbol symbol, LinkageProblemCause cause) {
    super("is not accessible", sourceClass, targetClass, symbol, cause);
  }

  @Override
  LinkageProblem withCause(LinkageProblemCause cause) {
    return new InaccessibleMemberProblem(getSourceClass(), getTargetClass(), getSymbol(), cause);
  }
  }
