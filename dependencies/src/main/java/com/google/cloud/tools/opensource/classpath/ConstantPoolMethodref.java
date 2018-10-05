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
 * A representation of an Methodref entry in constant pool.
 * In Java class file ConstantPool section,
 * a methodref entry has a pointer (index) of the actual value of class name, method name and its
 * type information
 * On the other hand, this class holds these values store directly so that we can read them easily
 */
class ConstantPoolMethodref {
  private String className;
  private String methodName;
  private String signature;

  public ConstantPoolMethodref(String className, String methodName, String signature) {
    this.className = className;
    this.methodName = methodName;
    this.signature = signature;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getSignature() {
    return signature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConstantPoolMethodref)) {
      return false;
    }
    ConstantPoolMethodref that = (ConstantPoolMethodref) o;
    return Objects.equals(className, that.className)
        && Objects.equals(methodName, that.methodName)
        && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(className, methodName, signature);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("className", className)
        .add("methodName", methodName)
        .add("signature", signature)
        .toString();
  }
}
