/*
 * Copyright 2019 Google LLC.
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

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.bcel.classfile.Utility;

/** Symbol for a method of class. */
public final class MethodSymbol extends Symbol {
  private final String name;
  private final String descriptor;
  private final boolean isInterfaceMethod;

  public MethodSymbol(String className, String name, String descriptor, boolean isInterfaceMethod) {
    super(className);
    this.name = checkNotNull(name);
    this.descriptor = checkNotNull(descriptor);
    this.isInterfaceMethod = isInterfaceMethod;
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

  /** Returns true if {@link #getClassName()} is an interface. */
  boolean isInterfaceMethod() {
    return isInterfaceMethod;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    if (!super.equals(other)) {
      return false;
    }
    MethodSymbol that = (MethodSymbol) other;
    return isInterfaceMethod == that.isInterfaceMethod
        && name.equals(that.name)
        && descriptor.equals(that.descriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, descriptor, isInterfaceMethod);
  }

  @Override
  public String toString() {
    return (isInterfaceMethod ? "Interface " : "") + getClassName() + "'s method " + Utility
        .methodSignatureToString(descriptor, name, "");
  }
}
