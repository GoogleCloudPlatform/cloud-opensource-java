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
 * The {@code classSymbol} with {@code modifier} is inaccessible to the {@code sourceClass} as per
 * {@code sourceClass}'s definition of the class symbol.
 *
 * <p>If the source class is in a different package than the target class, the class or one of its
 * enclosing types is not public. If the source class is in the same package, the class or one of
 * its enclosing types is private.
 */
final class InaccessibleClassProblem extends LinkageProblem {
  private AccessModifier modifier;

  InaccessibleClassProblem(
      ClassFile sourceClass, ClassFile targetClass, Symbol classSymbol, AccessModifier modifier) {
    super("is not accessible", sourceClass, classSymbol, targetClass);
    this.modifier = modifier;
  }

  @Override
  public final String toString() {
    StringBuilder message = new StringBuilder();
    message.append("Class " + getTargetClass().getBinaryName());
    switch (modifier) {
      case publicAccess:
        message.append(" is public");
        break;
      case privateAccess:
        message.append(" is private");
        break;
      case defaultAccess:
        message.append(" has default access");
    }

    message.append(" and referenced by " + getSourceClass().getBinaryName());
    if (modifier == AccessModifier.defaultAccess) {
      message.append("(different package)");
    }

    return message.toString();
  }

  @Override
  public String formatSymbolProblem() {
    String result = modifier.describe(getSymbol().toString());
    ClassFile targetClass = getTargetClass();
    if (targetClass != null) {
      String jarInfo = "(" + targetClass.getClassPathEntry() + ") ";
      result = jarInfo + result;
    }

    return result;
  }
}
