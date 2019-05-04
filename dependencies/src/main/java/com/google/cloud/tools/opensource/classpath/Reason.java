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

/** The kind of linkage error against a symbol reference. */
enum Reason {
  /** The target class of the symbol reference is not found in the class path. */
  CLASS_NOT_FOUND,

  /**
   * The referenced class or interface found in the class path is not compatible with the source.
   */
  INCOMPATIBLE_CLASS_CHANGE,

  /**
   * The target class of the symbol reference is inaccessible to the source.
   *
   * <p>If the source is in a different package, the class or one of its enclosing types is not
   * public. If the source is in the same package, the class or one of its enclosing types is
   * private.
   */
  INACCESSIBLE_CLASS,

  /**
   * The target member (method or field) is inaccessible to the source.
   *
   * <p>If the source is in a different package, the member is not public. If the source is in the
   * same package, the class is private. If the source is a subclass of the target class, the member
   * is not protected or public.
   */
  INACCESSIBLE_MEMBER,

  /** For a method or field reference, the symbol is not found in the target class. */
  SYMBOL_NOT_FOUND
}
