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

/**
 * A missing class linkage error.
 */
@AutoValue
public abstract class LinkageErrorMissingClass implements LinkageErrorWithReason {

  public abstract ClassSymbolReference getReference();

  public static LinkageErrorMissingClass errorAt(ClassSymbolReference reference) {
    return new AutoValue_LinkageErrorMissingClass(null, Reason.TARGET_CLASS_NOT_FOUND, reference);
  }

  public static LinkageErrorMissingClass errorWithModifierAt(URL targetClassLocation,
      ClassSymbolReference reference) {
    return new AutoValue_LinkageErrorMissingClass(targetClassLocation,
        Reason.INVALID_ACCESS_MODIFIER, reference);
  }
}
