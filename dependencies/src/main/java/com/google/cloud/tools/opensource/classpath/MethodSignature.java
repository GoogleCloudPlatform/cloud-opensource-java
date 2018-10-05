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

import com.google.common.base.MoreObjects;
import java.util.Objects;

/**
 * A representation of a tuple of method name and descriptor (type information) in a Method entry
 * in a class file. The descriptor part helps to distinguish method entries when method overloading
 * is used.
 */
public class MethodSignature {
  private String methodName;
  private String descriptor;

  public MethodSignature(String methodName, String descriptor) {
    this.methodName = methodName;
    this.descriptor = descriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MethodSignature)) {
      return false;
    }
    MethodSignature that = (MethodSignature) o;
    return Objects.equals(methodName, that.methodName) &&
        Objects.equals(descriptor, that.descriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methodName, descriptor);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("methodName", methodName)
        .add("descriptor", descriptor)
        .toString();
  }
}
