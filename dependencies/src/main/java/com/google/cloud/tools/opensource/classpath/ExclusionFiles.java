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
import com.google.common.collect.Multimap;
import com.thaiopensource.xml.sax.DraconianErrorHandler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
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
 * Utility for Linkage Checker exclusion files.
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
class ExclusionFiles {
  private static final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

  private static final QName LINKAGE_CHECKER_FILTER_TAG = QName.valueOf("LinkageCheckerFilter");
  private static final QName CLASS_TAG = QName.valueOf("Class");
  private static final QName LINKAGE_ERROR_TAG = QName.valueOf("LinkageError");
  private static final QName TARGET_TAG = QName.valueOf("Target");
  private static final QName SOURCE_TAG = QName.valueOf("Source");
  private static final QName METHOD_TAG = QName.valueOf("Method");
  private static final QName FIELD_TAG = QName.valueOf("Field");

  static ImmutableList<LinkageErrorMatcher> parse(Path exclusionFile)
      throws SAXException, IOException, VerifierConfigurationException {

    InputSource inputSource = new InputSource(Files.newInputStream(exclusionFile));
    inputSource.setSystemId(exclusionFile.toUri().toString());

    return parse(inputSource);
  }

  static ImmutableList<LinkageErrorMatcher> parse(URL exclusionFile)
      throws SAXException, IOException, VerifierConfigurationException {

    InputSource inputSource = new InputSource(exclusionFile.openStream());
    inputSource.setSystemId(exclusionFile.toString());

    return parse(inputSource);
  }

  private static ImmutableList<LinkageErrorMatcher> parse(InputSource inputSource)
      throws SAXException, IOException, VerifierConfigurationException {

    XMLReader reader = createXmlReader();

    ExclusionFileHandler handler = new ExclusionFileHandler();
    reader.setContentHandler(handler);

    reader.parse(inputSource);

    return handler.getMatchers();
  }

  private static XMLReader createXmlReader()
      throws SAXException, IOException, VerifierConfigurationException {
    // Validate and parse XML files in one pass using Jing validator as a filter.
    // http://iso-relax.sourceforge.net/JARV/JARV.html#use_42
    VerifierFactory factory = VerifierFactory.newInstance("http://relaxng.org/ns/structure/1.0");
    InputStream linkageCheckerSchema =
        ExclusionFiles.class.getClassLoader().getResourceAsStream("linkage-checker-exclusion.rng");
    Schema schema = factory.compileSchema(linkageCheckerSchema);
    Verifier verifier = schema.newVerifier();

    // DraconianErrorHandler throws SAXException upon invalid structure
    verifier.setErrorHandler(new DraconianErrorHandler());
    VerifierFilter filter = verifier.getVerifierFilter();
    filter.setParent(XMLReaderFactory.createXMLReader());
    return filter;
  }

  /** Writes {@code linkageErrors} as exclusion rules into {@code outputFile}. */
  static void write(Path outputFile, Multimap<SymbolProblem, ClassFile> linkageErrors)
      throws IOException, XMLStreamException, TransformerException {

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    XMLEventWriter writer = null;
    try {
      writer = XMLOutputFactory.newInstance().createXMLEventWriter(buffer);

      writer.add(eventFactory.createStartDocument());
      writer.add(eventFactory.createStartElement(LINKAGE_CHECKER_FILTER_TAG, null, null));

      for (SymbolProblem symbolProblem : linkageErrors.keySet()) {
        for (ClassFile classFile : linkageErrors.get(symbolProblem)) {
          writeXmlEvents(writer, symbolProblem, classFile);
        }
      }

      writer.add(eventFactory.createEndElement(LINKAGE_CHECKER_FILTER_TAG, null));
      writer.add(eventFactory.createEndDocument());

    } finally {
      if (writer != null) {
        writer.close();
      }
    }

    try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
      insertIndent(new ByteArrayInputStream(buffer.toByteArray()), outputStream);
    }
  }

  private static void insertIndent(InputStream inputStream, OutputStream outputStream)
      throws TransformerException {
    // Prefer Open JDK's default Transformer, rather than the one in net.sf.saxon:Saxon-HE. The
    // latter does not recognize "{http://xml.apache.org/xslt}indent-amount" property.
    System.setProperty(
        "javax.xml.transform.TransformerFactory",
        "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer indentTransformer = transformerFactory.newTransformer();
    indentTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
    // OpenJDK's default Transformer recognizes this property
    indentTransformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    // Add new line character after doctype declaration
    indentTransformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");

    indentTransformer.transform(new StreamSource(inputStream), new StreamResult(outputStream));
  }

  private static void writeXmlEvents(
      XMLEventWriter writer, SymbolProblem symbolProblem, ClassFile classFile)
      throws XMLStreamException {
    writer.add(eventFactory.createStartElement(LINKAGE_ERROR_TAG, null, null));

    writer.add(eventFactory.createStartElement(TARGET_TAG, null, null));
    writeXmlElement(writer, symbolProblem.getSymbol());
    writer.add(eventFactory.createEndElement(TARGET_TAG, null));

    writer.add(eventFactory.createStartElement(SOURCE_TAG, null, null));
    writeXmlElement(writer, classFile);
    writer.add(eventFactory.createEndElement(SOURCE_TAG, null));

    writer.add(eventFactory.createEndElement(LINKAGE_ERROR_TAG, null));
  }

  private static void writeXmlElement(XMLEventWriter writer, Symbol symbol)
      throws XMLStreamException {
    if (symbol instanceof ClassSymbol) {
      Attribute className = eventFactory.createAttribute("name", symbol.getClassBinaryName());
      StartElement event =
          eventFactory.createStartElement(CLASS_TAG, ImmutableList.of(className).iterator(), null);
      writer.add(event);

      writer.add(eventFactory.createEndElement(CLASS_TAG, null));

    } else if (symbol instanceof MethodSymbol) {
      MethodSymbol methodSymbol = (MethodSymbol) symbol;
      Attribute className = eventFactory.createAttribute("className", symbol.getClassBinaryName());
      Attribute methodName = eventFactory.createAttribute("name", methodSymbol.getName());
      StartElement event =
          eventFactory.createStartElement(
              METHOD_TAG, ImmutableList.of(className, methodName).iterator(), null);
      writer.add(event);

      writer.add(eventFactory.createEndElement(METHOD_TAG, null));

    } else if (symbol instanceof FieldSymbol) {
      FieldSymbol fieldSymbol = (FieldSymbol) symbol;
      Attribute className = eventFactory.createAttribute("className", symbol.getClassBinaryName());
      Attribute methodName = eventFactory.createAttribute("name", fieldSymbol.getName());

      StartElement event =
          eventFactory.createStartElement(
              FIELD_TAG, ImmutableList.of(className, methodName).iterator(), null);
      writer.add(event);

      writer.add(eventFactory.createEndElement(FIELD_TAG, null));
    }
  }

  private static void writeXmlElement(XMLEventWriter writer, ClassFile classFile)
      throws XMLStreamException {
    Attribute className = eventFactory.createAttribute("name", classFile.getBinaryName());

    writer.add(
        eventFactory.createStartElement(CLASS_TAG, ImmutableList.of(className).iterator(), null));
    writer.add(eventFactory.createEndElement(CLASS_TAG, null));
  }
}
