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

import com.google.common.collect.ImmutableSet;
import org.junit.Assert;
import org.junit.Test;

public class SymbolsInFileTest {
  @Test
  public void testCreation() {
    ClassSymbolReference classSymbolReference =
        ClassSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setSourceClassName("ClassB")
            .build();
    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassB")
            .build();
    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setTargetClassName("ClassC")
            .setFieldName("fieldX")
            .setSourceClassName("ClassD")
            .build();

    SymbolsInFile symbolsInFile = SymbolsInFile.builder().setClassReferences(
        ImmutableSet.of(classSymbolReference))
        .setFieldReferences(ImmutableSet.of(fieldSymbolReference))
        .setMethodReferences(ImmutableSet.of(methodSymbolReference))
        .build();

    Assert.assertEquals(classSymbolReference, symbolsInFile.getClassReferences().iterator().next());
    Assert.assertEquals(methodSymbolReference, symbolsInFile.getMethodReferences().iterator().next());
    Assert.assertEquals(fieldSymbolReference, symbolsInFile.getFieldReferences().iterator().next());
  }

}
