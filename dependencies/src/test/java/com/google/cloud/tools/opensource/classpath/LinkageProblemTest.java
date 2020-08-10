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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import com.google.common.truth.Truth;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class LinkageProblemTest {

  @Test
  public void testCreation() {
    // Confirming with a subclass of it
    ClassNotFoundProblem linkageProblem =
        new ClassNotFoundProblem(
            new ClassFile(new ClassPathEntry(Paths.get("aaa", "bbb.jar")), "java.lang.ABC"),
            new ClassSymbol("java.lang.Integer"));
    assertEquals(new ClassSymbol("java.lang.Integer"), linkageProblem.getSymbol());
  }

  @Test
  public void testNull() {
    new NullPointerTester()
        .setDefault(ClassSymbol.class, new ClassSymbol("java.lang.Integer"))
        .setDefault(
            ClassFile.class,
            new ClassFile(new ClassPathEntry(Paths.get("aaa", "bbb.jar")), "java.lang.ABC"))
        .testConstructors(ClassNotFoundProblem.class, Visibility.PACKAGE);
  }

  @Test
  public void testEquality() {
    new EqualsTester()
        .addEqualityGroup(
            new ClassNotFoundProblem(
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Object"),
                new ClassSymbol("java.lang.Integer")),
            new ClassNotFoundProblem(
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Object"),
                new ClassSymbol("java.lang.Integer")))
        .addEqualityGroup(
            new ClassNotFoundProblem(
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.ABC"),
                new ClassSymbol("java.lang.Integer")))
        .addEqualityGroup(
            new ClassNotFoundProblem(
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.Object"),
                new ClassSymbol("java.lang.ABC")))
        .addEqualityGroup(
            new AbstractMethodProblem(
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.A"),
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.B"),
                new MethodSymbol("java.lang.Integer", "intValue", "()Z", false)))
        .addEqualityGroup(
            new IncompatibleClassChangeProblem( // Only type is different from the one above
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.A"),
                new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "java.lang.B"),
                new ClassSymbol("java.lang.Integer")))
        .testEquals();
  }

  // Example linkage problems for formatting
  private Path path = Paths.get("aaa", "bbb-1.2.3.jar");

  private Artifact artifact1 =
      new DefaultArtifact("com.google:foo:0.0.1").setFile(new File("foo/foo.jar"));
  private ClassPathEntry entry1 = new ClassPathEntry(artifact1);
  private ClassFile source1 = new ClassFile(entry1, "java.lang.Object");

  private Artifact artifact2 =
      new DefaultArtifact("com.google:bar:0.0.1").setFile(new File("bar/bar.jar"));
  private ClassPathEntry entry2 = new ClassPathEntry(artifact2);
  private ClassFile source2 = new ClassFile(entry2, "java.lang.Integer");

  private LinkageProblem methodLinkageProblem =
      new SymbolNotFoundProblem(
          source1,
          new ClassFile(new ClassPathEntry(path), "java.lang.Object"),
          new MethodSymbol(
              "io.grpc.protobuf.ProtoUtils.marshaller",
              "marshaller",
              "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
              false));

  private LinkageProblem classLinkageProblem1 =
      new ClassNotFoundProblem(source1, new ClassSymbol("java.lang.Integer"));
  private LinkageProblem classLinkageProblem2 =
      new ClassNotFoundProblem(source2, new ClassSymbol("java.lang.Integer"));

  private Artifact artifact =
      new DefaultArtifact("com.google:ccc:1.2.3").setFile(new File("ccc-1.2.3.jar"));
  private ClassPathEntry entry = new ClassPathEntry(artifact);

  private LinkageProblem fieldLinkageProblem =
      new SymbolNotFoundProblem(
          source2,
          new ClassFile(entry, "java.lang.Integer"),
          new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I"));

  private ImmutableSet<LinkageProblem> linkageProblems =
      ImmutableSet.of(
          methodLinkageProblem, classLinkageProblem1, classLinkageProblem2, fieldLinkageProblem);

  @Test
  public void testFormatSymbolProblem() {
    assertEquals(
        "Class java.lang.Integer is not found", classLinkageProblem1.formatSymbolProblem());

    // classLinkageProblem1 and classLinkageProblem2 are the same except the sourceClass field.
    // formatSymbolProblem only contains the problem on symbol.
    assertEquals(
        classLinkageProblem1.formatSymbolProblem(), classLinkageProblem2.formatSymbolProblem());
  }

  @Test
  public void testFormatLinkageProblems() {
    assertEquals(
        "("
            + path
            + ") "
            + "io.grpc.protobuf.ProtoUtils.marshaller's method "
            + "marshaller(com.google.protobuf.Message) is not found;\n"
            + "  referenced by 1 class file\n"
            + "    java.lang.Object (com.google:foo:0.0.1)\n"
            + "Class java.lang.Integer is not found;\n"
            + "  referenced by 2 class files\n"
            + "    java.lang.Object (com.google:foo:0.0.1)\n"
            + "    java.lang.Integer (com.google:bar:0.0.1)\n"
            + "(com.google:ccc:1.2.3) java.lang.Integer's field MAX_VALUE is not found;\n"
            + "  referenced by 1 class file\n"
            + "    java.lang.Integer (com.google:bar:0.0.1)\n",
        LinkageProblem.formatLinkageProblems(linkageProblems));
  }

  @Test
  public void testGroupBySymbolProblems() {
    ImmutableMap<String, ImmutableSet<String>> grouped =
        LinkageProblem.groupBySymbolProblem(linkageProblems);

    Truth.assertThat(grouped.keySet())
        .containsExactly(
            methodLinkageProblem.formatSymbolProblem(),
            classLinkageProblem1.formatSymbolProblem(),
            fieldLinkageProblem.formatSymbolProblem())
        .inOrder();

    ImmutableSet<String> sourceClassNames = grouped.get(classLinkageProblem1.formatSymbolProblem());
    Truth.assertThat(sourceClassNames)
        .containsExactly("java.lang.Object", "java.lang.Integer")
        .inOrder();
  }
}
