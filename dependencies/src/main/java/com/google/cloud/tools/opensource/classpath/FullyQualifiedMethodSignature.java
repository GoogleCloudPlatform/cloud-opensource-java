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
 * A representation of a method with its descriptor (type information) and fully-qualified
 * class name.
 */
class FullyQualifiedMethodSignature {
  private String className;
  private MethodSignature methodSignature;

  /**
   * @param className fully-qualified class name
   * @param methodName method name
   * @param descriptor descriptor of the method
   */
  public FullyQualifiedMethodSignature(String className, String methodName, String descriptor) {
    this.className = className;
    this.methodSignature = new MethodSignature(methodName, descriptor);
  }

  public String getClassName() {
    return className;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FullyQualifiedMethodSignature)) {
      return false;
    }
    FullyQualifiedMethodSignature that = (FullyQualifiedMethodSignature) o;
    return Objects.equals(className, that.className) &&
        Objects.equals(methodSignature, that.methodSignature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(className, methodSignature);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("className", className)
        .add("methodSignature", methodSignature)
        .toString();
  }
}
