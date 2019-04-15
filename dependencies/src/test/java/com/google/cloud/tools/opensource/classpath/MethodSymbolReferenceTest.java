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

import org.junit.Assert;
import org.junit.Test;

public class MethodSymbolReferenceTest {
  
  private MethodSymbolReference methodSymbolReference =
      MethodSymbolReference.builder()
          .setTargetClassName("ClassA")
          .setInterfaceMethod(false)
          .setMethodName("methodX")
          .setDescriptor("java.lang.String")
          .setSourceClassName("ClassB")
          .build();

  @Test
  public void testCreation() {
    Assert.assertEquals("ClassA", methodSymbolReference.getTargetClassName());
    Assert.assertEquals("methodX", methodSymbolReference.getMethodName());
    Assert.assertEquals("java.lang.String", methodSymbolReference.getDescriptor());
    Assert.assertEquals("ClassB", methodSymbolReference.getSourceClassName());
  }

  @Test
  public void testGetErrorString() {
    Assert.assertEquals(
        "ClassA.methodX is not found, referenced from ClassB",
        methodSymbolReference.getErrorString());
  }

}
