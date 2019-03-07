/*
 * Copyright 2018 Google LLC.
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

import com.google.auto.value.AutoValue;
import org.apache.bcel.classfile.Utility;

/**
 * A symbol reference to a method of {@code targetClass} referenced from {@code sourceClass}.
 */
@AutoValue
abstract class MethodSymbolReference implements SymbolReference  {
  /**
   * Returns the method name of the reference. Example: {@code marshaller}
   */
  abstract String getMethodName();

  /** Returns true if {@code targetClassName} is an interface. */
  abstract boolean isInterfaceMethod();

  /**
   * Returns the descriptor of the method. A descriptor holds type information for its parameters
   * and return value. Example: {@code
   * '(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;}', where {@code Message}
   * class is the parameter and {@code MethodDescriptor$Marshaller} class is the return type.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">Java
   *     Virtual Machine Specification: Method Descriptors</a>
   */
  abstract String getDescriptor();

  static Builder builder() {
    return new AutoValue_MethodSymbolReference.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setTargetClassName(String className);
    abstract Builder setMethodName(String methodName);
    abstract Builder setInterfaceMethod(boolean isInterfaceMethod);
    abstract Builder setDescriptor(String descriptor);
    abstract Builder setSourceClassName(String className);
    abstract MethodSymbolReference build();
  }

  /** Returns string format close to declaration. For example {@code }.*/
  String methodDeclaration() {
    String methodSignature =
        Utility.methodSignatureToString(
            getDescriptor(),
            getMethodName(),
            "",
            true,
            null); // var0, var1 ... are used for arguments
    return methodSignature + " in " + getTargetClassName();
  }
}
