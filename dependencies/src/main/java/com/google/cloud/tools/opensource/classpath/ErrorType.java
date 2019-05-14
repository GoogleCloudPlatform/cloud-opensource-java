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
enum ErrorType {
  /** The target class of the symbol reference is not found in the class path. */
  CLASS_NOT_FOUND,

  /**
   * The referenced class or interface found in the class path is not binary-compatible with the
   * source class.
   *
   * <p>An example case of breaking binary-compatibility is when a superclass changes a method to
   * {@code final} and subclass is still overriding the method. Another example is when there is a
   * method call to an interface and the interface is changed to a class.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jls/se7/html/jls-13.html#jls-13.4.9">Java
   *     Language Specification: 13.4.9. final Fields and Constants</a>
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.4>Java
   *     Virtual Machine Specification: 5.4.3.4. Interface Method Resolution</a>
   */
  INCOMPATIBLE_CLASS_CHANGE,

  /**
   * The target class of the symbol reference is inaccessible to the source class.
   *
   * <p>If the source class is in a different package, the class or one of its enclosing types is
   * not public. If the source class is in the same package, the class or one of its enclosing types
   * is private.
   */
  INACCESSIBLE_CLASS,

  /**
   * The target member (method or field) is inaccessible to the source class.
   *
   * <p>If the source class is in a different package, the member is not public. If the source is in
   * the same package, the class is private. If the source is a subclass of the target class, the
   * member is not protected or public.
   */
  INACCESSIBLE_MEMBER,

  /** For a method or field reference, the symbol is not found in the target class. */
  SYMBOL_NOT_FOUND
}
