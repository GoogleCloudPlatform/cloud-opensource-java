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

import com.google.common.collect.ImmutableList;
import com.thaiopensource.xml.sax.DraconianErrorHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.iso_relax.verifier.Schema;
import org.iso_relax.verifier.Verifier;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.iso_relax.verifier.VerifierFactory;
import org.iso_relax.verifier.VerifierFilter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Parser for Linkage Checker exclusion files.
 *
 * <p>The exclusion file for Linkage Checker is an XML file. Its top-level element is
 * LinkageCheckerFilter. The XML file contains the following structure:
 *
 * <ul>
 *   <li>A LinkageCheckerFilter element has zero or more LinkageError elements.
 *   <li>A LinkageError element has at least one of Target element and Source element.
 *   <li>A Target element has a Package, Class, Method, or Field element. A Source element has an
 *       Artifact, Package, or Class element.
 *   <li>Method and Field elements have “className” attribute.
 * </ul>
 *
 * <p>Each type of the element works as a corresponding matcher, such as LinkageErrorMatcher for a
 * LinkageError element and SourceMatcher for Source element. Given a linkage error, they work as
 * below:
 *
 * <ul>
 *   <li>A LinkageErrorMatcher matches when all of its child elements match the linkage error.
 *   <li>A SourceMatcher matches a linkage error when the source class of the error matches its
 *       child element.
 *   <li>A TargetMatcher matches a linkage error when the target symbol (class, method, or field) of
 *       the error matches its child element.
 *   <li>A PackageMatcher matches the classes that have Java package specified by its name field.
 *       Prefix to specify child packages.
 *   <li>A ClassMatcher matches the class specified by its name attribute. ArtifactMatcher,
 *       PackageMatcher, and ClassMatcher also match methods and fields on their matching classes.
 *   <li>A MethodMatcher matches method symbol specified by className and name attribute.
 *   <li>A FieldMatcher matches field symbol specified by className and name attribute.
 * </ul>
 */
class ExclusionFileParser {

  static ImmutableList<LinkageErrorMatcher> parse(Path exclusionFile)
      throws SAXException, IOException, VerifierConfigurationException {

    XMLReader reader = createParser();

    ExclusionFileHandler handler = new ExclusionFileHandler();
    reader.setContentHandler(handler);

    InputSource inputSource = new InputSource(Files.newInputStream(exclusionFile));
    inputSource.setSystemId(exclusionFile.toUri().toString());
    reader.parse(inputSource);

    return handler.getMatchers();
  }

  private static XMLReader createParser()
      throws SAXException, IOException, VerifierConfigurationException {
    // Validate and parse XML files in one pass using Jing validator as a filter.
    // http://iso-relax.sourceforge.net/JARV/JARV.html#use_42
    VerifierFactory factory = VerifierFactory.newInstance("http://relaxng.org/ns/structure/1.0");
    InputStream linkageCheckerSchema =
        ExclusionFileParser.class
            .getClassLoader()
            .getResourceAsStream("linkage-checker-exclusion-relax-ng.xml");
    Schema schema = factory.compileSchema(linkageCheckerSchema);
    Verifier verifier = schema.newVerifier();

    // DraconianErrorHandler throws SAXException upon invalid structure
    verifier.setErrorHandler(new DraconianErrorHandler());
    VerifierFilter filter = verifier.getVerifierFilter();
    filter.setParent(XMLReaderFactory.createXMLReader());
    return filter;
  }
}
