/*
 * Copyright 2020 Google LLC.
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

/**
 * Linkage Checker cannot determine the cause of the linkage error.
 *
 * <p>This occurs when the POM file that used in building a library and the published POM file are
 * different.
 */
public class UnknownCause extends LinkageProblemCause {

  @Override
  public String toString() {
    return "Unknown";
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UnknownCause;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
