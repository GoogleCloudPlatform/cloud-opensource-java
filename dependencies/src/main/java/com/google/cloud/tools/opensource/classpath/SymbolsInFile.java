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
 * Symbolic references and defined classes in a file (jar file or class file). The
 * symbolic references are from constant pool of class file(s). The defined class names are
 * from the class defined as top-level in a class file and inner classes in it.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4">Java
 *     Virtual Machine Specification: The Constant Pool</a>
 */
@AutoValue
abstract class SymbolsInFile {
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
  /**
   * Returns class names defined in the file.
   */
  abstract ImmutableSet<String> getDefinedClassNames();

  static Builder builder() {
    return new AutoValue_SymbolsInFile.Builder();
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
    abstract SymbolsInFile build();
  }
}
