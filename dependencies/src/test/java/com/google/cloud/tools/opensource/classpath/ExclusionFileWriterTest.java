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
    ImmutableSetMultimap<SymbolProblem, ClassFile> linkageErrors =
        ImmutableSetMultimap.of(
            methodSymbolProblem,
                new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source1"),
            classSymbolProblem,
                new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source2"));
    ExclusionFileWriter.write(output, linkageErrors);

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(output);
    Truth.assertThat(matchers).hasSize(2);

    LinkageErrorMatcher linkageErrorMatcher = matchers.get(0);
    boolean match =
        linkageErrorMatcher.match(
            methodSymbolProblem,
            new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source1"));
    assertTrue(match);
  }
}
