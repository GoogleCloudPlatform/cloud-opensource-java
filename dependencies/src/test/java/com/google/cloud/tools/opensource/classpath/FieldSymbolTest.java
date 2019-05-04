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

public class FieldSymbolTest {

  @Test
  public void testFieldSymbolCreation() {
    FieldSymbol fieldSymbol = new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I");
    assertEquals("java.lang.Integer", fieldSymbol.getClassName());
    assertEquals("MAX_VALUE", fieldSymbol.getName());
    assertEquals("I", fieldSymbol.getDescriptor());

    new NullPointerTester().testConstructors(FieldSymbol.class, Visibility.PACKAGE);
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
        .addEqualityGroup(new MethodSymbol("java.lang.Integer", "MAX_VALUE", "I"))
        .testEquals();
  }
}
