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

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import org.junit.Test;

public class MethodSymbolTest {

  @Test
  public void testMethodSymbolCreation() {
    MethodSymbol methodSymbol =
        new MethodSymbol("java.lang.Object", "equals", "(Ljava/lang/Object;)Z", false);
    assertEquals("java.lang.Object", methodSymbol.getClassBinaryName());
    assertEquals("equals", methodSymbol.getName());
    assertEquals("(Ljava/lang/Object;)Z", methodSymbol.getDescriptor());
  }

  @Test
  public void testNull() {
    new NullPointerTester().testConstructors(MethodSymbol.class, Visibility.PACKAGE);
  }

  @Test
  public void testMethodSymbolEquality() {
    new EqualsTester()
        .addEqualityGroup(
            new MethodSymbol("java.lang.Object", "equals", "(Ljava/lang/Object;)Z", false),
            new MethodSymbol("java.lang.Object", "equals", "(Ljava/lang/Object;)Z", false))
        .addEqualityGroup(
            new MethodSymbol("java.lang.Object", "hashCode", "(Ljava/lang/Object;)Z", false))
        .addEqualityGroup(new MethodSymbol("Object", "equals", "(Ljava/lang/Object;)Z", false))
        .addEqualityGroup(new MethodSymbol("java.lang.Object", "equals", "(I)Lcom.Bar;", false))
        .addEqualityGroup(
            new MethodSymbol("java.lang.Object", "equals", "(Ljava/lang/Object;)Z", true))
        .addEqualityGroup(new ClassSymbol("java.lang.Object"))
        .addEqualityGroup(new FieldSymbol("java.lang.Object", "equals", "(Ljava/lang/Object;)Z"))
        .testEquals();
  }

  @Test
  public void testToString() {
    MethodSymbol symbol =
        new MethodSymbol(
            "io.grpc.protobuf.ProtoUtils",
            "marshaller",
            "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
            false);
    assertEquals(
        "io.grpc.protobuf.ProtoUtils's method marshaller(com.google.protobuf.Message)",
        symbol.toString());
  }

  @Test
  public void testGetMethodNameWithSignature() {
    MethodSymbol symbol =
        new MethodSymbol(
            "io.grpc.protobuf.ProtoUtils",
            "marshaller",
            "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
            false);
    assertEquals(
        "io.grpc.MethodDescriptor$Marshaller marshaller(com.google.protobuf.Message)",
        symbol.getMethodNameWithSignature());
  }
}
