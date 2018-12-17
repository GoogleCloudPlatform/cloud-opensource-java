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
import java.net.URL;
import javax.annotation.Nullable;

/**
 * A missing field linkage error.
 */
@AutoValue
abstract class LinkageErrorMissingField {
  abstract FieldSymbolReference getReference();

  /**
   * Returns the location of the target class in the field reference; null if the target class is
   * not found in the class path.
   */
  @Nullable
  abstract URL getTargetClassLocation();

  static LinkageErrorMissingField errorAt(
      FieldSymbolReference reference, @Nullable URL targetClassLocation) {
    return new AutoValue_LinkageErrorMissingField(reference, targetClassLocation);
  }
}
