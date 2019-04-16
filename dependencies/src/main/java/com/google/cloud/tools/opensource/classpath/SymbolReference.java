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

/**
 * A reference to a symbol of {@code targetClass} from {@code sourceClass}. The values of the class
 * names are fully-qualified form known as binary names. For example {@code
 * io.grpc.MethodDescriptor$MethodType}.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.1">Java
 *     Language Specification: 13.1. The Form of a Binary</a>
 */
interface SymbolReference {
  /**
   * Returns the fully-qualified class name (binary name) of the source class of the reference.
   */
  String getSourceClassName();

  /**
   * Returns the fully-qualified class or interface name (binary name) of the target class of the
   * reference.
   */
  String getTargetClassName();

  /**
   * Returns a string describing the missing reference for display to an end user.
   */
  String getErrorString();
}
