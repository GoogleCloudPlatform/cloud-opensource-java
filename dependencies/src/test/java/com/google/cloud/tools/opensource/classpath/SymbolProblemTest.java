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
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class SymbolProblemTest {

  @Test
  public void testCreation() {
    SymbolProblem symbolProblem =
        new SymbolProblem(
            new ClassSymbol("java.lang.Integer"),
            ErrorType.CLASS_NOT_FOUND,
            new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Object"));
    assertSame(ErrorType.CLASS_NOT_FOUND, symbolProblem.getErrorType());
    assertEquals(new ClassSymbol("java.lang.Integer"), symbolProblem.getSymbol());
    assertEquals(
        new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Object"),
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
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Object")),
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Object")))
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Long"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Object")))
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(new ClassPathEntry(Paths.get("abc", "bar.jar")), "java.lang.Object")))
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"),
                ErrorType.CLASS_NOT_FOUND,
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Long")))
        .addEqualityGroup(
            new SymbolProblem(
                new ClassSymbol("java.lang.Integer"), ErrorType.CLASS_NOT_FOUND, null))
        .testEquals();
  }

  @Test
  public void testFormatSymbolProblems() {
    Path path = Paths.get("aaa", "bbb-1.2.3.jar");
    SymbolProblem methodSymbolProblem =
        new SymbolProblem(
            new MethodSymbol(
                "io.grpc.protobuf.ProtoUtils.marshaller",
                "marshaller",
                "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
                false),
            ErrorType.SYMBOL_NOT_FOUND,
            new ClassFile(new ClassPathEntry(path), "java.lang.Object"));

    SymbolProblem classSymbolProblem =
        new SymbolProblem(new ClassSymbol("java.lang.Integer"), ErrorType.CLASS_NOT_FOUND, null);

    Artifact artifact = new DefaultArtifact("com.google:ccc:1.2.3")
        .setFile(new File("ccc-1.2.3.jar"));
    ClassPathEntry entry = new ClassPathEntry(artifact);  
  
    SymbolProblem fieldSymbolProblem =
        new SymbolProblem(
            new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I"),
            ErrorType.SYMBOL_NOT_FOUND,
            new ClassFile(entry, "java.lang.Integer"));

    Artifact artifact1 = new DefaultArtifact("com.google:foo:0.0.1")
        .setFile(new File("foo/foo.jar"));
    ClassPathEntry entry1 = new ClassPathEntry(artifact1);  
    ClassFile source1 = new ClassFile(entry1, "java.lang.Object");

    Artifact artifact2 = new DefaultArtifact("com.google:bar:0.0.1")
        .setFile(new File("bar/bar.jar"));
    ClassPathEntry entry2 = new ClassPathEntry(artifact2);  
    ClassFile source2 = new ClassFile(entry2, "java.lang.Integer");

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
        "("
            + path
            + ") "
            + "io.grpc.protobuf.ProtoUtils.marshaller's method "
            + "marshaller(com.google.protobuf.Message arg1) is not found;\n"
            + "  referenced by 1 class file\n"
            + "    java.lang.Object (com.google:foo:0.0.1)\n"
            + "Class java.lang.Integer is not found;\n"
            + "  referenced by 2 class files\n"
            + "    java.lang.Object (com.google:foo:0.0.1)\n"
            + "    java.lang.Integer (com.google:bar:0.0.1)\n"
            + "(com.google:ccc:1.2.3) java.lang.Integer's field MAX_VALUE is not found;\n"
            + "  referenced by 1 class file\n"
            + "    java.lang.Integer (com.google:bar:0.0.1)\n",
        SymbolProblem.formatSymbolProblems(symbolProblems));
  }
}
