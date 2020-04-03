/*
 * Copyright 2020 Google LLC.
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

import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.stream.XMLStreamException;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.junit.Test;
import org.xml.sax.SAXException;

public class ExclusionFileWriterTest {
  @Test
  public void testExclusionFileCreation()
      throws IOException, XMLStreamException, VerifierConfigurationException, SAXException {

    Path output = Files.createTempFile("output", ".xml");

    SymbolProblem methodSymbolProblem =
        new SymbolProblem(
            new MethodSymbol(
                "io.grpc.protobuf.ProtoUtils",
                "marshaller",
                "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
                false),
            ErrorType.SYMBOL_NOT_FOUND,
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "java.lang.Object"));

    SymbolProblem classSymbolProblem =
        new SymbolProblem(new ClassSymbol("java.lang.Integer"), ErrorType.CLASS_NOT_FOUND, null);

    SymbolProblem fieldSymbolProblem =
        new SymbolProblem(
            new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I"),
            ErrorType.SYMBOL_NOT_FOUND,
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "java.lang.Integer"));

    ImmutableSetMultimap<SymbolProblem, ClassFile> linkageErrors =
        ImmutableSetMultimap.of(
            methodSymbolProblem,
                new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source1"),
            fieldSymbolProblem,
            new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source2"),
            classSymbolProblem,
                new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source3"));
    ExclusionFileWriter.write(output, linkageErrors);

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(output);
    Truth.assertThat(matchers).hasSize(3);

    LinkageErrorMatcher matcher0 = matchers.get(0);
    boolean methodMatch =
        matcher0.match(
            methodSymbolProblem,
            new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source1"));
    assertTrue(methodMatch);

    LinkageErrorMatcher matcher1 = matchers.get(1);
    boolean fieldMatch =
        matcher1.match(
            fieldSymbolProblem,
            new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source2"));
    assertTrue(fieldMatch);

    LinkageErrorMatcher matcher2 = matchers.get(2);
    boolean classMatch =
        matcher2.match(
            classSymbolProblem,
            new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source3"));
    assertTrue(classMatch);
  }
}
