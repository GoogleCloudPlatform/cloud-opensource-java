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
 * A table of symbolic references and defined classes in a jar file or class file. The
 * symbolic references are from constant pool of class files.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4">Java
 *     Virtual Machine Specification: The Constant Pool</a>
 */
@AutoValue
abstract class SymbolTable {
  abstract ImmutableSet<ClassSymbolReference> getClassReferences();
  abstract ImmutableSet<FieldSymbolReference> getFieldReferences();
  abstract ImmutableSet<MethodSymbolReference> getMethodReferences();
  abstract ImmutableSet<String> getDefinedClassNames();

  static Builder builder() {
    return new AutoValue_SymbolTable.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setClassReferences(Iterable<ClassSymbolReference> classReferences);
    abstract Builder setFieldReferences(Iterable<FieldSymbolReference> fieldReferences);
    abstract Builder setMethodReferences(Iterable<MethodSymbolReference> methodReferences);
    abstract Builder setDefinedClassNames(Iterable<String> classNames);
    abstract ImmutableSet.Builder<ClassSymbolReference> classReferencesBuilder();
    abstract ImmutableSet.Builder<MethodSymbolReference> methodReferencesBuilder();
    abstract ImmutableSet.Builder<FieldSymbolReference> fieldReferencesBuilder();
    abstract ImmutableSet.Builder<String> definedClassNamesBuilder();
    abstract SymbolTable build();
  }
}
