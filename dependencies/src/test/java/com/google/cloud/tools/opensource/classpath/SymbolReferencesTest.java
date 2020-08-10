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

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

public class SymbolReferencesTest {
  
  private Path path = Paths.get("foo", "bar.jar");
  private ClassFile sourceClass;
  private ClassSymbol classSymbol = new ClassSymbol("java.util.concurrent.TimeoutException");
  private MethodSymbol methodSymbol =
      new MethodSymbol(
          "com.google.common.base.Preconditions",
          "checkNotNull",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
          false);

  private FieldSymbol fieldSymbol =
      new FieldSymbol("com.google.common.util.concurrent.Monitor$Guard", "waiterCount", "I");

  @Before 
  public void setUp() throws IOException {
    sourceClass = new ClassFile(new ClassPathEntry(path), "com.google.Foo");
  }
  
  @Test
  public void testCreation() {
    SymbolReferences.Builder builder = new SymbolReferences.Builder();

    builder.addClassReference(sourceClass, classSymbol);
    builder.addMethodReference(sourceClass, methodSymbol);
    builder.addFieldReference(sourceClass, fieldSymbol);

    SymbolReferences references = builder.build();

    Truth.assertThat(references.getClassSymbols(sourceClass)).contains(classSymbol);
    Truth.assertThat(references.getMethodSymbols(sourceClass)).contains(methodSymbol);
    Truth.assertThat(references.getFieldSymbols(sourceClass)).contains(fieldSymbol);
  }

  @Test
  public void testEquality() throws IOException {
    SymbolReferences.Builder builder1 = new SymbolReferences.Builder();
    builder1.addClassReference(sourceClass, classSymbol);
    builder1.addMethodReference(sourceClass, methodSymbol);
    builder1.addFieldReference(sourceClass, fieldSymbol);

    SymbolReferences.Builder builder2 = new SymbolReferences.Builder();
    builder2.addClassReference(sourceClass, classSymbol);
    builder2.addMethodReference(sourceClass, methodSymbol);
    builder2.addFieldReference(sourceClass, fieldSymbol);

    ClassFile sourceClass2 = new ClassFile(new ClassPathEntry(path), "com.google.Bar");
    SymbolReferences.Builder builder3 = new SymbolReferences.Builder();
    builder3.addClassReference(sourceClass2, classSymbol);
    builder3.addMethodReference(sourceClass, methodSymbol);
    builder3.addFieldReference(sourceClass, fieldSymbol);

    SymbolReferences.Builder builder4 = new SymbolReferences.Builder();
    builder4.addClassReference(sourceClass, classSymbol);
    builder4.addMethodReference(sourceClass2, methodSymbol);
    builder4.addFieldReference(sourceClass, fieldSymbol);

    SymbolReferences.Builder builder5 = new SymbolReferences.Builder();
    builder5.addClassReference(sourceClass, classSymbol);
    builder5.addMethodReference(sourceClass, methodSymbol);
    builder5.addFieldReference(sourceClass2, fieldSymbol);

    new EqualsTester()
        .addEqualityGroup(builder1.build(), builder2.build())
        .addEqualityGroup(builder3.build())
        .addEqualityGroup(builder4.build())
        .addEqualityGroup(builder5.build())
        .testEquals();
  }

  @Test
  public void testNull() {
    new NullPointerTester().testConstructors(SymbolReferences.class, Visibility.PACKAGE);
  }

  @Test
  public void testAddAll() throws IOException {
    SymbolReferences.Builder builder1 = new SymbolReferences.Builder();
    SymbolReferences.Builder builder2 = new SymbolReferences.Builder();

    builder1.addClassReference(sourceClass, classSymbol);
    builder1.addMethodReference(sourceClass, methodSymbol);
    builder1.addFieldReference(sourceClass, fieldSymbol);

    ClassFile sourceClass2 = new ClassFile(new ClassPathEntry(path), "com.google.Bar");
    builder2.addClassReference(sourceClass2, classSymbol);
    builder2.addMethodReference(sourceClass2, methodSymbol);
    builder2.addFieldReference(sourceClass2, fieldSymbol);

    builder1.addAll(builder2);
    SymbolReferences references = builder1.build();

    Truth.assertThat(references.getClassSymbols(sourceClass)).contains(classSymbol);
    Truth.assertThat(references.getMethodSymbols(sourceClass)).contains(methodSymbol);
    Truth.assertThat(references.getFieldSymbols(sourceClass)).contains(fieldSymbol);
    Truth.assertThat(references.getClassSymbols(sourceClass2)).contains(classSymbol);
    Truth.assertThat(references.getMethodSymbols(sourceClass2)).contains(methodSymbol);
    Truth.assertThat(references.getFieldSymbols(sourceClass2)).contains(fieldSymbol);
  }
}
