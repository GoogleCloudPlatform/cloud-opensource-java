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

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class ExclusionFilesTest {

  private ClassFile sourceClass =
      new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source");

  private LinkageProblem methodLinkageProblem =
      new SymbolNotFoundProblem(
          new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source1"),
          new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "java.lang.Object"),
          new MethodSymbol(
              "io.grpc.protobuf.ProtoUtils",
              "marshaller",
              "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
              false));

  private LinkageProblem fieldLinkageProblem =
      new SymbolNotFoundProblem(
          new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source2"),
          new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "java.lang.Integer"),
          new FieldSymbol("java.lang.Integer", "MAX_VALUE", "I"));

  private LinkageProblem classLinkageProblem =
      new ClassNotFoundProblem(
          new ClassFile(new ClassPathEntry(Paths.get("source.jar")), "com.foo.Source3"),
          new ClassSymbol("java.lang.Integer"));

  private ImmutableSet<LinkageProblem> linkageErrors =
      ImmutableSet.of(methodLinkageProblem, fieldLinkageProblem, classLinkageProblem);

  private Path output;

  @Before
  public void setup() throws IOException {
    output = Files.createTempFile("output", ".xml");
    output.toFile().deleteOnExit();
  }

  @Test
  public void testParse_sourceClass()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-class.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);
    boolean result =
        matcher.match(
            new SymbolNotFoundProblem(
                new ClassFile(
                    new ClassPathEntry(Paths.get("dummy.jar")), "reactor.core.publisher.Traces"),
                new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "java.lang.Object"),
                new MethodSymbol(
                    "io.grpc.protobuf.ProtoUtils",
                    "marshaller",
                    "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
                    false)
                // This filter works for the line below
                ));
    assertTrue(result);
  }

  @Test
  public void testParse_targetField()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-field.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageProblem linkageProblemToMatch =
        new InaccessibleMemberProblem(
            sourceClass,
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "com.google.Foo"),
            new FieldSymbol("com.google.Foo", "fieldA", "Ljava.lang.String;"));
    boolean result = matchers.get(0).match(linkageProblemToMatch);
    assertTrue(result);
  }

  @Test
  public void testParse_targetMethod()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-method.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    LinkageProblem linkageProblemToMatch =
        new InaccessibleMemberProblem(
            sourceClass,
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "com.google.Foo"),
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false));
    boolean result = matcher.match(linkageProblemToMatch);
    assertTrue(result);
  }

  @Test
  public void testParse_targetPackage()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-package.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    LinkageProblem linkageProblemToMatch =
        new InaccessibleMemberProblem(
            sourceClass,
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "com.google.Foo"),
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false));
    boolean result = matcher.match(linkageProblemToMatch);
    assertTrue(result);
  }

  @Test
  public void testParse_targetPackage_subpackage()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-package.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    // Package "com.google" should match "com.google.cloud.Foo"
    LinkageProblem linkageProblemToMatch =
        new InaccessibleMemberProblem(
            sourceClass,
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "com.cloud.google.Foo"),
            new MethodSymbol("com.google.cloud.Foo", "methodA", "()Ljava.lang.String;", false));
    boolean result = matcher.match(linkageProblemToMatch);
    assertTrue(result);
  }

  @Test
  public void testParse_targetPackage_unmatch()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-package.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    // Package com.googler is not a subpackage of com.google.
    LinkageProblem linkageProblemToMatch =
        new InaccessibleMemberProblem(
            sourceClass,
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "com.googler.Foo"),
            new MethodSymbol("com.googler.Foo", "methodA", "()Ljava.lang.String;", false));
    boolean result = matcher.match(linkageProblemToMatch);
    assertFalse(result);
  }

  @Test
  public void testParse_sourceAndTarget_match()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-and-target.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    LinkageProblem linkageProblemToMatch =
        new InaccessibleMemberProblem(
            new ClassFile(
                new ClassPathEntry(Paths.get("dummy.jar")), "reactor.core.publisher.Traces"),
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "com.google.Foo"),
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false));
    boolean result = matcher.match(linkageProblemToMatch);
    assertTrue(result);
  }

  @Test
  public void testParse_sourceAndTarget_unmatch()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-and-target.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    LinkageProblem linkageProblemToMatch =
        new InaccessibleMemberProblem(
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "com.google.Bar"),
            new ClassFile(new ClassPathEntry(Paths.get("dummy.jar")), "com.google.Foo"),
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false));
    boolean result = matcher.match(linkageProblemToMatch); // No match
    assertFalse(result);
  }

  @Test
  public void testParse_namespaceException()
      throws URISyntaxException, SAXException, IOException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-with-namespace.xml");
    try {
      ExclusionFiles.parse(exclusionFile);
      fail();
    } catch (SAXParseException expected) {
      // pass
      assertEquals(
          "element \"foo:Class\" not allowed anywhere; expected element \"Class\", \"Field\", "
              + "\"Method\" or \"Package\"",
          expected.getMessage());
      assertEquals(4, expected.getLineNumber());
    }
  }

  @Test
  public void testParse_sourceMethod()
      throws URISyntaxException, SAXException, IOException, VerifierConfigurationException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-method.xml");
    try {
      ExclusionFiles.parse(exclusionFile);
      fail();
    } catch (SAXParseException expected) {
      assertEquals(
          "element \"Method\" not allowed here; expected element \"Class\" or \"Package\"",
          expected.getMessage());
      assertEquals(4, expected.getLineNumber());
    }
  }

  @Test
  public void testParse_exceptionSystemId()
      throws URISyntaxException, SAXException, IOException, VerifierConfigurationException {
    String resourceName = "exclusion-sample-rules/source-method.xml";
    Path exclusionFile = absolutePathOfResource(resourceName);
    try {
      ExclusionFiles.parse(exclusionFile);
      fail();
    } catch (SAXParseException expected) {
      assertEquals(4, expected.getLineNumber());
      Truth.assertThat(expected.getSystemId()).endsWith(resourceName);
      String systemId = expected.getSystemId();
      Truth.assertThat(systemId).startsWith("file:");
      Truth.assertThat(systemId).endsWith(resourceName);
    }
  }

  @Test
  public void testParse_duplicateSourceElements()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    String resourceName = "exclusion-sample-rules/duplicate-source-element.xml";
    Path exclusionFile = absolutePathOfResource(resourceName);
    try {
      ExclusionFiles.parse(exclusionFile);
      fail();
    } catch (SAXParseException expected) {
      assertEquals(
          "element \"Source\" not allowed here; "
              + "expected the element end-tag or element \"Reason\"",
          expected.getMessage());
      assertEquals(9, expected.getLineNumber());
    }
  }

  @Test
  public void testParse_reasonElement()
      throws URISyntaxException, IOException, SAXException, VerifierConfigurationException {
    String resourceName = "exclusion-sample-rules/reason.xml";
    Path exclusionFile = absolutePathOfResource(resourceName);

    // Should not raise exception
    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
  }

  @Test
  public void testExclusionFileCreation()
      throws IOException, XMLStreamException, VerifierConfigurationException, SAXException,
          TransformerException {

    ExclusionFiles.write(output, linkageErrors);

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFiles.parse(output);
    Truth.assertThat(matchers).hasSize(3);

    LinkageErrorMatcher matcher0 = matchers.get(0);
    boolean methodMatch = matcher0.match(methodLinkageProblem);
    assertTrue(methodMatch);

    LinkageErrorMatcher matcher1 = matchers.get(1);
    boolean fieldMatch = matcher1.match(fieldLinkageProblem);
    assertTrue(fieldMatch);

    LinkageErrorMatcher matcher2 = matchers.get(2);
    boolean classMatch = matcher2.match(classLinkageProblem);
    assertTrue(classMatch);
  }

  @Test
  public void testWriteExclusionFile_indent()
      throws IOException, XMLStreamException, TransformerException, URISyntaxException {

    ExclusionFiles.write(output, linkageErrors);

    String actual = new String(Files.readAllBytes(output));

    String expected =
        new String(
            Files.readAllBytes(
                absolutePathOfResource(
                    "exclusion-sample-rules/expected-exclusion-output-file.xml")),
            Charsets.UTF_8);

    assertEquals(expected, actual);
  }
}
