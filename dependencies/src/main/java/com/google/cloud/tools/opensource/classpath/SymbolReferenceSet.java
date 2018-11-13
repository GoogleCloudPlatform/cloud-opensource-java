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
import com.google.common.collect.ImmutableSet;

/**
 * A set of symbolic references in a file (jar file or class file). The references are from constant
 * pool of class file(s).
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4">Java
 *     Virtual Machine Specification: The Constant Pool</a>
 */
@AutoValue
abstract class SymbolReferenceSet {
  /**
   * Returns class references from the file.
   */
  abstract ImmutableSet<ClassSymbolReference> getClassReferences();

  /**
   * Returns method references from the file.
   */
  abstract ImmutableSet<MethodSymbolReference> getMethodReferences();

  /**
   * Returns field references from the file.
   */
  abstract ImmutableSet<FieldSymbolReference> getFieldReferences();

  static Builder builder() {
    return new AutoValue_SymbolReferenceSet.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setClassReferences(Iterable<ClassSymbolReference> classReferences);
    abstract Builder setFieldReferences(Iterable<FieldSymbolReference> fieldReferences);
    abstract Builder setMethodReferences(Iterable<MethodSymbolReference> methodReferences);
    abstract ImmutableSet.Builder<ClassSymbolReference> classReferencesBuilder();
    abstract ImmutableSet.Builder<MethodSymbolReference> methodReferencesBuilder();
    abstract ImmutableSet.Builder<FieldSymbolReference> fieldReferencesBuilder();

    abstract SymbolReferenceSet build();

    Builder merge(SymbolReferenceSet other) {
      classReferencesBuilder().addAll(other.getClassReferences());
      methodReferencesBuilder().addAll(other.getMethodReferences());
      fieldReferencesBuilder().addAll(other.getFieldReferences());
      return this;
    }
  }
}
