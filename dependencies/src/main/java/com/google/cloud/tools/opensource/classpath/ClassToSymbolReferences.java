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

import com.google.common.collect.ImmutableSetMultimap;
import java.util.Objects;

/**
 * A set of symbol references from source class to target symbols (class, method, and field
 * symbols).
 */
class ClassToSymbolReferences {
  private final ImmutableSetMultimap<ClassAndJar, ClassSymbol> classToClassSymbols;
  private final ImmutableSetMultimap<ClassAndJar, MethodSymbol> classToMethodSymbols;
  private final ImmutableSetMultimap<ClassAndJar, FieldSymbol> classToFieldSymbols;

  ImmutableSetMultimap<ClassAndJar, ClassSymbol> getClassToClassSymbols() {
    return classToClassSymbols;
  }

  ImmutableSetMultimap<ClassAndJar, MethodSymbol> getClassToMethodSymbols() {
    return classToMethodSymbols;
  }

  ImmutableSetMultimap<ClassAndJar, FieldSymbol> getClassToFieldSymbols() {
    return classToFieldSymbols;
  }

  private ClassToSymbolReferences(
      ImmutableSetMultimap<ClassAndJar, ClassSymbol> classToClassSymbols,
      ImmutableSetMultimap<ClassAndJar, MethodSymbol> classToMethodSymbols,
      ImmutableSetMultimap<ClassAndJar, FieldSymbol> classToFieldSymbols) {
    this.classToClassSymbols = checkNotNull(classToClassSymbols);
    this.classToMethodSymbols = checkNotNull(classToMethodSymbols);
    this.classToFieldSymbols = checkNotNull(classToFieldSymbols);
  }

  static class Builder {
    private final ImmutableSetMultimap.Builder<ClassAndJar, ClassSymbol> classToClassSymbols;
    private final ImmutableSetMultimap.Builder<ClassAndJar, MethodSymbol> classToMethodSymbols;
    private final ImmutableSetMultimap.Builder<ClassAndJar, FieldSymbol> classToFieldSymbols;

    Builder() {
      classToClassSymbols = ImmutableSetMultimap.builder();
      classToMethodSymbols = ImmutableSetMultimap.builder();
      classToFieldSymbols = ImmutableSetMultimap.builder();
    }

    Builder addClassReference(ClassAndJar source, ClassSymbol symbol) {
      classToClassSymbols.put(source, symbol);
      return this;
    }

    Builder addMethodReference(ClassAndJar source, MethodSymbol symbol) {
      classToMethodSymbols.put(source, symbol);
      return this;
    }

    Builder addFieldReference(ClassAndJar source, FieldSymbol symbol) {
      classToFieldSymbols.put(source, symbol);
      return this;
    }

    ClassToSymbolReferences build() {
      return new ClassToSymbolReferences(
          classToClassSymbols.build(), classToMethodSymbols.build(), classToFieldSymbols.build());
    }

    Builder addAll(Builder other) {
      classToClassSymbols.putAll(other.classToClassSymbols.build());
      classToMethodSymbols.putAll(other.classToMethodSymbols.build());
      classToFieldSymbols.putAll(other.classToFieldSymbols.build());
      return this;
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ClassToSymbolReferences that = (ClassToSymbolReferences) other;
    return classToClassSymbols.equals(that.classToClassSymbols)
        && classToMethodSymbols.equals(that.classToMethodSymbols)
        && classToFieldSymbols.equals(that.classToFieldSymbols);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classToClassSymbols, classToMethodSymbols, classToFieldSymbols);
  }
}
