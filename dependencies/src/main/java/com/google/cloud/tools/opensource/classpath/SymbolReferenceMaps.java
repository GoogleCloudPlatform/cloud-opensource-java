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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSetMultimap;
import java.util.Objects;

/**
 * A set of symbol references from source class to target symbols (class, method, and field
 * symbols).
 */
class SymbolReferenceMaps {
  private final ImmutableSetMultimap<ClassFile, ClassSymbol> classToClassSymbols;
  private final ImmutableSetMultimap<ClassFile, MethodSymbol> classToMethodSymbols;
  private final ImmutableSetMultimap<ClassFile, FieldSymbol> classToFieldSymbols;

  ImmutableSetMultimap<ClassFile, ClassSymbol> getClassToClassSymbols() {
    return classToClassSymbols;
  }

  ImmutableSetMultimap<ClassFile, MethodSymbol> getClassToMethodSymbols() {
    return classToMethodSymbols;
  }

  ImmutableSetMultimap<ClassFile, FieldSymbol> getClassToFieldSymbols() {
    return classToFieldSymbols;
  }

  @VisibleForTesting
  SymbolReferenceMaps(
      ImmutableSetMultimap<ClassFile, ClassSymbol> classToClassSymbols,
      ImmutableSetMultimap<ClassFile, MethodSymbol> classToMethodSymbols,
      ImmutableSetMultimap<ClassFile, FieldSymbol> classToFieldSymbols) {
    this.classToClassSymbols = checkNotNull(classToClassSymbols);
    this.classToMethodSymbols = checkNotNull(classToMethodSymbols);
    this.classToFieldSymbols = checkNotNull(classToFieldSymbols);
  }

  static class Builder {
    private final ImmutableSetMultimap.Builder<ClassFile, ClassSymbol> classToClassSymbols;
    private final ImmutableSetMultimap.Builder<ClassFile, MethodSymbol> classToMethodSymbols;
    private final ImmutableSetMultimap.Builder<ClassFile, FieldSymbol> classToFieldSymbols;

    Builder() {
      classToClassSymbols = ImmutableSetMultimap.builder();
      classToMethodSymbols = ImmutableSetMultimap.builder();
      classToFieldSymbols = ImmutableSetMultimap.builder();
    }

    Builder addClassReference(ClassFile source, ClassSymbol symbol) {
      classToClassSymbols.put(source, symbol);
      return this;
    }

    Builder addMethodReference(ClassFile source, MethodSymbol symbol) {
      classToMethodSymbols.put(source, symbol);
      return this;
    }

    Builder addFieldReference(ClassFile source, FieldSymbol symbol) {
      classToFieldSymbols.put(source, symbol);
      return this;
    }

    SymbolReferenceMaps build() {
      return new SymbolReferenceMaps(
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
    SymbolReferenceMaps that = (SymbolReferenceMaps) other;
    return classToClassSymbols.equals(that.classToClassSymbols)
        && classToMethodSymbols.equals(that.classToMethodSymbols)
        && classToFieldSymbols.equals(that.classToFieldSymbols);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classToClassSymbols, classToMethodSymbols, classToFieldSymbols);
  }
}
