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
 * The {@code symbol} (class or interface) defined in {@code targetClass} is not binary-compatible
 * with the {@code sourceClass}.
 *
 * <p>An example case of breaking binary-compatibility is when a superclass changes a method to
 * {@code final} and a subclass is still overriding the method. Another example is when there is a
 * method call to an interface and the interface is changed to a class with the same name.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html#jls-13.4.9">Java
 *     Language Specification: 13.4.9. final Fields and static Constant Variables</a>
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.4">Java
 *     Virtual Machine Specification: 5.4.3.4. Interface Method Resolution</a>
 */
final class IncompatibleClassChangeProblem extends IncompatibleLinkageProblem {

  IncompatibleClassChangeProblem(ClassFile sourceClass, ClassFile targetClass, Symbol symbol) {
    super("has changed incompatibly", sourceClass, targetClass, symbol);
  }
}
