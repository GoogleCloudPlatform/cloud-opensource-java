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
 * A missing field linkage error.
 */
@AutoValue
abstract class LinkageErrorMissingField implements LinkageErrorWithReason {
  // TODO(#295): Consolidate LinkageErrorMissingXXX classes into generic one

  abstract FieldSymbolReference getReference();

  static LinkageErrorMissingField errorMissingTargetClass(
      FieldSymbolReference reference) {
    return builder().setReference(reference).setReason(Reason.CLASS_NOT_FOUND).build();
  }

  static LinkageErrorMissingField errorSymbolNotFound(
      FieldSymbolReference reference, URL targetClassLocation) {
    return builder()
        .setReference(reference)
        .setReason(Reason.SYMBOL_NOT_FOUND)
        .setTargetClassLocation(targetClassLocation)
        .build();
  }

  private static LinkageErrorMissingField.Builder builder() {
    return new AutoValue_LinkageErrorMissingField.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {

    abstract LinkageErrorMissingField.Builder setTargetClassLocation(URL targetClassLocation);

    abstract LinkageErrorMissingField.Builder setReason(Reason reason);

    abstract LinkageErrorMissingField.Builder setReference(FieldSymbolReference reference);

    abstract LinkageErrorMissingField build();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(getReference());
    builder.append(", reason: " + getReason());
    if (getTargetClassLocation() != null) {
      builder.append(", target class from " + getTargetClassLocation());
    } else {
      builder.append(", target class location not found");
    }
    return builder.toString();
  }
}
