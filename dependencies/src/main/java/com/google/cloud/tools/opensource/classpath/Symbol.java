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

/**
 * The referent of the symbolic references (class, method, or field references) in the run-time
 * constant pool of JVM.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.1">Java
 *     Virtual Machine Specification: The Run-Time Constant Pool</a>
 */
abstract class Symbol {
  /**
   * Returns the binary name of the class that contains the symbol. If this is a class symbol, the
   * class name itself.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.1">Java
   *     Language Specification: 13.1. The Form of a Binary</a>
   */
  abstract String getClassName();
}
