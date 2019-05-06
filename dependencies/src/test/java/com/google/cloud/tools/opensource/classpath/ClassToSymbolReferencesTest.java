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
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class ClassToSymbolReferencesTest {
  private Path path = Paths.get("foo", "bar.jar");
  private ClassAndJar sourceClass = new ClassAndJar(path, "com.google.Foo");
  private ClassSymbol classSymbol = new ClassSymbol("java.util.concurrent.TimeoutException");
  private MethodSymbol methodSymbol =
      new MethodSymbol(
          "com.google.common.base.Preconditions",
          "checkNotNull",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
          false);

  private FieldSymbol fieldSymbol =
      new FieldSymbol("com.google.common.util.concurrent.Monitor$Guard", "waiterCount", "I");

  @Test
  public void testCreation() {
    ClassToSymbolReferences.Builder builder = new ClassToSymbolReferences.Builder();

    builder.addClassReference(sourceClass, classSymbol);
    builder.addMethodReference(sourceClass, methodSymbol);
    builder.addFieldReference(sourceClass, fieldSymbol);

    ClassToSymbolReferences references = builder.build();

    Truth.assertThat(references.getClassToClassSymbols()).containsEntry(sourceClass, classSymbol);
    Truth.assertThat(references.getClassToMethodSymbols()).containsEntry(sourceClass, methodSymbol);
    Truth.assertThat(references.getClassToFieldSymbols()).containsEntry(sourceClass, fieldSymbol);
  }

  @Test
  public void testEquality() {
    ClassToSymbolReferences.Builder builder1 = new ClassToSymbolReferences.Builder();
    builder1.addClassReference(sourceClass, classSymbol);
    builder1.addMethodReference(sourceClass, methodSymbol);
    builder1.addFieldReference(sourceClass, fieldSymbol);

    ClassToSymbolReferences.Builder builder2 = new ClassToSymbolReferences.Builder();
    builder2.addClassReference(sourceClass, classSymbol);
    builder2.addMethodReference(sourceClass, methodSymbol);
    builder2.addFieldReference(sourceClass, fieldSymbol);

    ClassAndJar sourceClass2 = new ClassAndJar(path, "com.google.Bar");
    ClassToSymbolReferences.Builder builder3 = new ClassToSymbolReferences.Builder();
    builder3.addClassReference(sourceClass2, classSymbol);
    builder3.addMethodReference(sourceClass, methodSymbol);
    builder3.addFieldReference(sourceClass, fieldSymbol);

    ClassToSymbolReferences.Builder builder4 = new ClassToSymbolReferences.Builder();
    builder4.addClassReference(sourceClass, classSymbol);
    builder4.addMethodReference(sourceClass2, methodSymbol);
    builder4.addFieldReference(sourceClass, fieldSymbol);

    ClassToSymbolReferences.Builder builder5 = new ClassToSymbolReferences.Builder();
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
    new NullPointerTester().testConstructors(ClassToSymbolReferences.class, Visibility.PACKAGE);
  }

  @Test
  public void testAddAll() {
    ClassToSymbolReferences.Builder builder1 = new ClassToSymbolReferences.Builder();
    ClassToSymbolReferences.Builder builder2 = new ClassToSymbolReferences.Builder();

    builder1.addClassReference(sourceClass, classSymbol);
    builder1.addMethodReference(sourceClass, methodSymbol);
    builder1.addFieldReference(sourceClass, fieldSymbol);

    ClassAndJar sourceClass2 = new ClassAndJar(path, "com.google.Bar");
    builder2.addClassReference(sourceClass2, classSymbol);
    builder2.addMethodReference(sourceClass2, methodSymbol);
    builder2.addFieldReference(sourceClass2, fieldSymbol);

    builder1.addAll(builder2);
    ClassToSymbolReferences references = builder1.build();

    Truth.assertThat(references.getClassToClassSymbols()).containsEntry(sourceClass, classSymbol);
    Truth.assertThat(references.getClassToMethodSymbols()).containsEntry(sourceClass, methodSymbol);
    Truth.assertThat(references.getClassToFieldSymbols()).containsEntry(sourceClass, fieldSymbol);
    Truth.assertThat(references.getClassToClassSymbols()).containsEntry(sourceClass2, classSymbol);
    Truth.assertThat(references.getClassToMethodSymbols())
        .containsEntry(sourceClass2, methodSymbol);
    Truth.assertThat(references.getClassToFieldSymbols()).containsEntry(sourceClass2, fieldSymbol);
  }
}
