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
 * Symbol for a super class. This symbol is a special case of {@link ClassSymbol} when it is
 * referenced only from its subclasses. Treating super class symbols apart from {@link ClassSymbol}
 * helps to validate the relationship between a superclass and its subclasses.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10">Java
 *     Virtual Machine Specification: 4.10. Verification of class Files</a>
 */
final class SuperClassSymbol extends ClassSymbol {
  SuperClassSymbol(String className) {
    super(className);
  }
}
