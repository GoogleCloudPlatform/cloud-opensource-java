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

import com.google.common.collect.ImmutableSetMultimap;
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

  @Test
  public void testFormatSymbolProblems() {
    SymbolProblem methodSymbolProblem =
        new SymbolProblem(
            new MethodSymbol(
                "io.grpc.protobuf.ProtoUtils.marshaller",
                "marshaller",
                "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
                false),
            ErrorType.SYMBOL_NOT_FOUND,
            new ClassFile(Paths.get("aaa", "bbb-1.2.3.jar"), "java.lang.Object"));

    SymbolProblem classSymbolProblem =
        new SymbolProblem(new ClassSymbol("java.lang.Integer"), ErrorType.CLASS_NOT_FOUND, null);

    SymbolProblem fieldSymbolProblem =
        new SymbolProblem(
            new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I"),
            ErrorType.SYMBOL_NOT_FOUND,
            new ClassFile(Paths.get("ccc-1.2.3.jar"), "java.lang.Integer"));

    ClassFile source1 = new ClassFile(Paths.get("foo", "dummy.jar"), "java.lang.Object");
    ClassFile source2 = new ClassFile(Paths.get("bar", "dummy.jar"), "java.lang.Object");

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        ImmutableSetMultimap.of(
            methodSymbolProblem,
            source1,
            classSymbolProblem,
            source1,
            classSymbolProblem,
            source2,
            fieldSymbolProblem,
            source2);
    assertEquals(
        "(bbb-1.2.3.jar)io.grpc.protobuf.ProtoUtils.marshaller's method io.grpc.MethodDescriptor$Marshaller "
            + "marshaller(com.google.protobuf.Message arg1) is not found in the class\n"
            + "  referenced by 1 class file\n"
            + "Class java.lang.Integer is not found\n"
            + "  referenced by 2 class files\n"
            + "(ccc-1.2.3.jar)java.lang.Integer's field MAX_VALUE is not found in the class\n"
            + "  referenced by 1 class file\n",
        SymbolProblem.formatSymbolProblems(symbolProblems));
  }
}
