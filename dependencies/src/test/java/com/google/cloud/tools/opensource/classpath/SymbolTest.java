/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.opensource.classpath;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SymbolTest {

  @Test
  public void testClassSymbolCreation() {
    ClassSymbol classSymbol = ClassSymbol.builder().setClassName("java.lang.Object").build();
    assertEquals(classSymbol.getClassName(), "java.lang.Object");
  }

  @Test
  public void testMethodSymbolCreation() {
    MethodSymbol methodSymbol =
        MethodSymbol.builder().setClassName("java.lang.Object").setName("equals")
            .setDescriptor("(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;")
            .build();
    assertEquals("java.lang.Object", methodSymbol.getClassName());
    assertEquals("equals", methodSymbol.getName());
    assertEquals("(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
        methodSymbol.getDescriptor());
  }

  @Test
  public void testFieldSymbolCreation() {
    MethodSymbol fieldSymbol = MethodSymbol.builder().setClassName("java.lang.Integer")
        .setName("MAX_VALUE").setDescriptor("I").build();
    assertEquals("java.lang.Integer", fieldSymbol.getClassName());
    assertEquals("MAX_VALUE", fieldSymbol.getName());
    assertEquals("I", fieldSymbol.getDescriptor());
  }

}
