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
 * The result of static linkage check per a jar file
 */
@AutoValue
abstract class JarLinkageReport {
  static JarLinkageReport create(Path jarPath, ImmutableList<MissingClass> missingClasses,
      ImmutableList<MissingMethod> missingMethods, ImmutableList<MissingField> missingFields) {
    return new AutoValue_JarLinkageReport(jarPath, missingClasses, missingMethods, missingFields);
  }

  /**
   * @return the jar file containing source classes of linkage errors
   */
  abstract Path jarPath();

  abstract ImmutableList<MissingClass> missingClasses();
  abstract ImmutableList<MissingMethod> missingMethods();
  abstract ImmutableList<MissingField> missingFields();
}
