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

import static org.junit.Assert.assertEquals;

import com.google.common.testing.EqualsTester;
import org.junit.Test;

public class SymbolTest {

  @Test
  public void testClassSymbolCreation() {
    ClassSymbol classSymbol = new ClassSymbol("java.lang.Object");
    assertEquals(classSymbol.getClassName(), "java.lang.Object");
  }

  @Test
  public void testClassSymbolEquality() {
    new EqualsTester()
        .addEqualityGroup(new ClassSymbol("java.lang.Object"), new ClassSymbol("java.lang.Object"))
        .addEqualityGroup(new ClassSymbol("java.lang.Long"))
        .testEquals();
  }

  @Test
  public void testMethodSymbolCreation() {
    MethodSymbol methodSymbol =
        new MethodSymbol(
            "java.lang.Object",
            "equals",
            "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;");
    assertEquals("java.lang.Object", methodSymbol.getClassName());
    assertEquals("equals", methodSymbol.getName());
    assertEquals(
        "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
        methodSymbol.getDescriptor());
  }

  @Test
  public void testMethodSymbolEquality() {
    new EqualsTester()
        .addEqualityGroup(
            new MethodSymbol("java.lang.Object", "equals", "foo"),
            new MethodSymbol("java.lang.Object", "equals", "foo"))
        .addEqualityGroup(new MethodSymbol("java.lang.Object", "hashCode", "foo"))
        .addEqualityGroup(new MethodSymbol("Object", "equals", "foo"))
        .addEqualityGroup(new MethodSymbol("java.lang.Object", "equals", "bar"))
        .testEquals();
  }

  @Test
  public void testFieldSymbolCreation() {
    FieldSymbol fieldSymbol = new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I");
    assertEquals("java.lang.Integer", fieldSymbol.getClassName());
    assertEquals("MAX_VALUE", fieldSymbol.getName());
    assertEquals("I", fieldSymbol.getDescriptor());
  }

  @Test
  public void testFieldSymbolEquality() {
    new EqualsTester()
        .addEqualityGroup(
            new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I"),
            new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I"))
        .addEqualityGroup(new FieldSymbol("java.lang.Float", "MAX_VALUE", "I"))
        .addEqualityGroup(new FieldSymbol("java.lang.Integer", "MIN_VALUE", "I"))
        .addEqualityGroup(new FieldSymbol("java.lang.Integer", "MAX_VALUE", "F"))
        .testEquals();
  }
}
