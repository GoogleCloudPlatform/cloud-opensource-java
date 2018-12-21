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

import java.net.URL;
import javax.annotation.Nullable;

/** Interface to provide common fields for different types of static linkage errors. */
interface LinkageErrorWithReason {

  /**
   * Returns the location of the target class in a symbol reference; null if the target class is not
   * found in the class path or the source location is unavailable.
   */
  @Nullable
  URL getTargetClassLocation();

  /** Returns the reason why a symbol reference is marked as a linkage error. */
  Reason getReason();

  /** Reason to distinguish the cause of a static linkage error against a symbol reference. */
  enum Reason {
    /** The target class of the symbol reference is not found in the class path. */
    CLASS_NOT_FOUND,

    /**
     * The symbol is inaccessible to the source.
     *
     * <p>If the source is in a different package, the symbol or one of its enclosing types is not
     * public. If the source is in the same package, the symbol or one of its enclosing types is
     * private.
     */
    INACCESSIBLE,

    /**
     * For a method or field reference, the symbol is not found in the target class in the class
     * path.
     */
    SYMBOL_NOT_FOUND
  }
}
