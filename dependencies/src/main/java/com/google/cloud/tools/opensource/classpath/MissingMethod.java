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
 * Representation of a linkage error where {@code sourceClassName} expects {@code methodName}
 * in {@code className}, however the method with the {@code descriptor} is not available in the
 * class or is inaccessible to {@code sourceClassName}
 */
@AutoValue
abstract class MissingMethod {
  abstract String className();
  abstract String methodName();
  abstract String descriptor();
  abstract String sourceClassName();

  static Builder builder() {
    return new AutoValue_MissingMethod.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setClassName(String value);
    abstract Builder setMethodName(String value);
    abstract Builder setDescriptor(String value);
    abstract Builder setSourceClassName(String value);
    abstract MissingMethod build();
  }
}
