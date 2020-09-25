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

import org.apache.bcel.classfile.Utility;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * The {@code sourceClass} references the {@code expectedMethodSymbol}, but the {@code
 * expectedMethodSymbol.getClassBinaryName} has the method with a different return type ({@code
 * actualTypeName}).
 */
class ReturnTypeChangedProblem extends LinkageProblem {
  private String actualType;

  ReturnTypeChangedProblem(
      ClassFile sourceClass,
      @Nullable ClassFile targetClass,
      MethodSymbol expectedMethodSymbol,
      String actualType) {
    super(
        "is expected to return "
            + Utility.methodSignatureReturnType(expectedMethodSymbol.getDescriptor())
            + " but instead returns "
            + actualType,
        sourceClass,
        expectedMethodSymbol,
        targetClass);
    this.actualType = actualType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), actualType);
  }

  @Override
  public boolean equals(Object other) {
    return super.equals(other) // this checks the class equality
        && Objects.equals(actualType, ((ReturnTypeChangedProblem) other).actualType);
  }
}
