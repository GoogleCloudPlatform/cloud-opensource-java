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

import java.util.Objects;

/** A symbol for a method of class. */
class MethodSymbol extends Symbol {
  private final String className;
  private final String name;
  private final String descriptor;

  public MethodSymbol(String className, String name, String descriptor) {
    this.className = className;
    this.name = name;
    this.descriptor = descriptor;
  }

  @Override
  String getClassName() {
    return className;
  }

  /** Returns the name of the method. */
  String getName() {
    return name;
  }

  /**
   * Returns the descriptor of the method. A descriptor holds type information for its parameters
   * and return value. Example: '{@code
   * (Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;}', where {@code Message}
   * class is the parameter and {@code MethodDescriptor$Marshaller} class is the return type.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">Java
   *     Virtual Machine Specification: Method Descriptors</a>
   */
  String getDescriptor() {
    return descriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MethodSymbol that = (MethodSymbol) o;
    return Objects.equals(className, that.className)
        && Objects.equals(name, that.name)
        && Objects.equals(descriptor, that.descriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(className, name, descriptor);
  }
}
