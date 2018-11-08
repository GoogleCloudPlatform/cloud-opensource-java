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
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/**
 * The result of checking linkage linkages in one jar file.
 */
@AutoValue
abstract class JarLinkageReport {
  /**
   * Returns the absolute path of the jar file containing source classes of linkage errors
   */
  abstract Path getJarPath();

  abstract ImmutableList<LinkageErrorMissingClass> getMissingClassErrors();
  abstract ImmutableList<LinkageErrorMissingMethod> getMissingMethodErrors();
  abstract ImmutableList<LinkageErrorMissingField> getMissingFieldErrors();

  static Builder builder() {
    return new AutoValue_JarLinkageReport.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setJarPath(Path value);
    abstract Builder setMissingClassErrors(Iterable<LinkageErrorMissingClass> value);
    abstract Builder setMissingMethodErrors(Iterable<LinkageErrorMissingMethod> value);
    abstract Builder setMissingFieldErrors(Iterable<LinkageErrorMissingField> value);
    abstract JarLinkageReport build();
  }
}
