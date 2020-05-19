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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.util.Objects;

/**
 * The symbol references found in class files.
 */
class SymbolReferences {
  
  // TODO this is still wonky. A ClassFile should have symbol references,
  // not be mapped to symbol references
  
  private final ImmutableSetMultimap<ClassFile, ClassSymbol> classToClassSymbols;
  private final ImmutableSetMultimap<ClassFile, MethodSymbol> classToMethodSymbols;
  private final ImmutableSetMultimap<ClassFile, FieldSymbol> classToFieldSymbols;
  private final ImmutableSet<ClassFile> classFiles;

  ImmutableSet<MethodSymbol> getMethodSymbols(ClassFile classFile) {
    return classToMethodSymbols.get(classFile);
  }

  ImmutableSet<ClassFile> getClassFiles() {
    return this.classFiles;
  }

  ImmutableSet<FieldSymbol> getFieldSymbols(ClassFile classFile) {
    return classToFieldSymbols.get(classFile);
  }

  ImmutableSet<ClassSymbol> getClassSymbols(ClassFile classFile) {
    return classToClassSymbols.get(classFile);
  }

  private SymbolReferences(
      ImmutableSet<ClassFile> classFiles,
      ImmutableSetMultimap<ClassFile, ClassSymbol> classToClassSymbols,
      ImmutableSetMultimap<ClassFile, MethodSymbol> classToMethodSymbols,
      ImmutableSetMultimap<ClassFile, FieldSymbol> classToFieldSymbols) {
    this.classToClassSymbols = checkNotNull(classToClassSymbols);
    this.classToMethodSymbols = checkNotNull(classToMethodSymbols);
    this.classToFieldSymbols = checkNotNull(classToFieldSymbols);
    this.classFiles = checkNotNull(classFiles);
  }

  static class Builder {
    private final ImmutableSetMultimap.Builder<ClassFile, ClassSymbol> classToClassSymbols;
    private final ImmutableSetMultimap.Builder<ClassFile, MethodSymbol> classToMethodSymbols;
    private final ImmutableSetMultimap.Builder<ClassFile, FieldSymbol> classToFieldSymbols;
    private final ImmutableSet.Builder<ClassFile> classFiles = ImmutableSet.builder();

    Builder() {
      classToClassSymbols = ImmutableSetMultimap.builder();
      classToMethodSymbols = ImmutableSetMultimap.builder();
      classToFieldSymbols = ImmutableSetMultimap.builder();
    }

    Builder addClassReference(ClassFile source, ClassSymbol symbol) {
      classToClassSymbols.put(source, symbol);
      classFiles.add(source);
      return this;
    }

    Builder addMethodReference(ClassFile source, MethodSymbol symbol) {
      classToMethodSymbols.put(source, symbol);
      classFiles.add(source);
      return this;
    }

    Builder addFieldReference(ClassFile source, FieldSymbol symbol) {
      classToFieldSymbols.put(source, symbol);
      return this;
    }

    SymbolReferences build() {
      return new SymbolReferences(classFiles.build(),
          classToClassSymbols.build(), classToMethodSymbols.build(), classToFieldSymbols.build());
    }

    Builder addAll(Builder other) {
      classToClassSymbols.putAll(other.classToClassSymbols.build());
      classToMethodSymbols.putAll(other.classToMethodSymbols.build());
      classToFieldSymbols.putAll(other.classToFieldSymbols.build());
      classFiles.addAll(other.classFiles.build());
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
    SymbolReferences that = (SymbolReferences) other;
    return classToClassSymbols.equals(that.classToClassSymbols)
        && classToMethodSymbols.equals(that.classToMethodSymbols)
        && classToFieldSymbols.equals(that.classToFieldSymbols);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classToClassSymbols, classToMethodSymbols, classToFieldSymbols);
  }
}
