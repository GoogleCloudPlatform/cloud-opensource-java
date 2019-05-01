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

/**
 * A symbol reference to {@code targetClass} referenced from {@code sourceClass}.
 */
@AutoValue
abstract class ClassSymbolReference implements SymbolReference {

  /** Returns true if the symbol reference is from a class to its superclass. */
  abstract boolean isSubclass();
  
  @Override
  public String getErrorString() {
    return getTargetClassName() + " is not found, referenced from " + getSourceClassName();
  }

  static Builder builder() {
    return new AutoValue_ClassSymbolReference.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setSubclass(boolean isSubclass);
    abstract Builder setTargetClassName(String className);
    abstract Builder setSourceClassName(String className);
    abstract ClassSymbolReference build();
  }
}
