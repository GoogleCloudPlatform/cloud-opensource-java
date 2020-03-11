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

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class ExclusionFileParserTest {

  @Test
  public void testParse_sourceClass() throws URISyntaxException, IOException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-class.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);
    boolean result =
        matcher.match(null, new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_targetField() throws URISyntaxException, IOException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-field.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new FieldSymbol("com.google.Foo", "fieldA", "Ljava.lang.String;"),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matchers
            .get(0)
            .match(
                symbolProblemToMatch,
                new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_targetMethod() throws URISyntaxException, IOException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-method.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_targetPackage() throws URISyntaxException, IOException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-package.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_targetPackage_subpackage()
      throws URISyntaxException, IOException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-package.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    // Package "com.google" should match "com.google.cloud.Foo"
    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.google.cloud.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.cloud.google.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_targetPackage_unmatch()
      throws URISyntaxException, IOException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-package.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    // Package com.googler is not a subpackage of com.google.
    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.googler.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.googler.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertFalse(result);
  }

  @Test
  public void testParse_sourceAndTarget_match()
      throws URISyntaxException, IOException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-and-target.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "reactor.core.publisher.Traces"));
    assertTrue(result);
  }

  @Test
  public void testParse_sourceAndTarget_unmatch()
      throws URISyntaxException, IOException, SAXException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-and-target.xml");

    ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
    Truth.assertThat(matchers).hasSize(1);
    LinkageErrorMatcher matcher = matchers.get(0);

    SymbolProblem symbolProblemToMatch =
        new SymbolProblem(
            new MethodSymbol("com.google.Foo", "methodA", "()Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Foo"));
    boolean result =
        matcher.match(
            symbolProblemToMatch,
            new ClassFile(Paths.get("dummy.jar"), "com.google.Bar")); // No match
    assertFalse(result);
  }

  @Test
  public void testParse_namespaceException() throws URISyntaxException, SAXException, IOException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/target-with-namespace.xml");
    try {
      ImmutableList<LinkageErrorMatcher> matchers = ExclusionFileParser.parse(exclusionFile);
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
  public void testParse_sourceMethod() throws URISyntaxException, SAXException, IOException {
    Path exclusionFile = absolutePathOfResource("exclusion-sample-rules/source-method.xml");
    try {
      ExclusionFileParser.parse(exclusionFile);
      fail();
    } catch (SAXParseException expected) {
      assertEquals(
          "element \"Method\" not allowed here; expected element \"Class\" or \"Package\"",
          expected.getMessage());
      assertEquals(4, expected.getLineNumber());
    }
  }

  @Test
  public void testParse_exceptionSystemId() throws URISyntaxException, SAXException, IOException {
    String resourceName = "exclusion-sample-rules/source-method.xml";
    Path exclusionFile = absolutePathOfResource(resourceName);
    try {
      ExclusionFileParser.parse(exclusionFile);
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
      throws URISyntaxException, IOException, SAXException {
    String resourceName = "exclusion-sample-rules/duplicate-source-element.xml";
    Path exclusionFile = absolutePathOfResource(resourceName);
    try {
      ExclusionFileParser.parse(exclusionFile);
      fail();
    } catch (SAXParseException expected) {
      assertEquals(
          "element \"Source\" not allowed here; expected the element end-tag",
          expected.getMessage());
      assertEquals(9, expected.getLineNumber());
    }
  }
}
