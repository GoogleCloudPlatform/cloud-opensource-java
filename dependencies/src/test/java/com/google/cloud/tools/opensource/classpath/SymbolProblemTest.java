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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import java.nio.file.Paths;
import org.junit.Test;

public class SymbolProblemTest {

  @Test
  public void testCreation() {
    SymbolProblem symbolProblem =
        new SymbolProblem(
            new ClassSymbol("java.lang.Integer"),
            ErrorType.CLASS_NOT_FOUND,
            new ClassFile(Paths.get("foo", "bar.jar"), "java.lang.Object"));
    assertSame(ErrorType.CLASS_NOT_FOUND, symbolProblem.getErrorType());
    assertEquals(new ClassSymbol("java.lang.Integer"), symbolProblem.getSymbol());
    assertEquals(
        new ClassFile(Paths.get("foo", "bar.jar"), "java.lang.Object"),
        symbolProblem.getContainingClass());
  }

  @Test
  public void testNull() {
    new NullPointerTester()
        .setDefault(Symbol.class, new ClassSymbol("java.lang.Integer"))
        .testConstructors(SymbolProblem.class, Visibility.PACKAGE);
  }

  @Test
  public void testEquality() {
    new EqualsTester()
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(Paths.get("foo", "bar.jar"), "java.lang.Object")),
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(Paths.get("foo", "bar.jar"), "java.lang.Object")))
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Long"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(Paths.get("foo", "bar.jar"), "java.lang.Object")))
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(Paths.get("abc", "bar.jar"), "java.lang.Object")))
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(Paths.get("foo", "bar.jar"), "java.lang.Long")))
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"), ErrorType.CLASS_NOT_FOUND, null))
        .testEquals();
  }
}
