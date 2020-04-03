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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import org.iso_relax.verifier.Schema;
import org.iso_relax.verifier.Verifier;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.iso_relax.verifier.VerifierFactory;
import org.iso_relax.verifier.VerifierFilter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/** Writer for Linkage Checker exclusion files. */
class ExclusionFileWriter {

  static final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

  /** Writes {@code linkageErrors} as exclusion rules into {@code outputFile}. */
  static void write(Path outputFile, Multimap<SymbolProblem, ClassFile> linkageErrors)
      throws IOException, XMLStreamException {

    XMLEventWriter writer = null;
    try {
      writer =
          XMLOutputFactory.newInstance().createXMLEventWriter(Files.newOutputStream(outputFile));

      writer.add(eventFactory.createStartDocument());
      QName linkageCheckerFilter = QName.valueOf("LinkageCheckerFilter");
      writer.add(
          eventFactory.createStartElement(linkageCheckerFilter, null, null));

      for (SymbolProblem symbolProblem : linkageErrors.keySet()) {
        for (ClassFile classFile : linkageErrors.get(symbolProblem)) {
          writeXmlEvents(writer, symbolProblem, classFile);
        }
      }

      writer.add(eventFactory.createEndElement(linkageCheckerFilter, null));
      writer.add(eventFactory.createEndDocument());
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  static void writeXmlEvents(
      XMLEventWriter writer, SymbolProblem symbolProblem, ClassFile classFile)
      throws XMLStreamException {
    QName linkageErrorTagName = QName.valueOf("LinkageError");
    writer.add(eventFactory.createStartElement(linkageErrorTagName, null, null));

    QName targetTagName = QName.valueOf("Target");
    writer.add(eventFactory.createStartElement(targetTagName, null, null));
    writeXmlElement(writer, symbolProblem.getSymbol());
    writer.add(eventFactory.createEndElement(targetTagName, null));

    QName sourceTagName = QName.valueOf("Source");
    writer.add(eventFactory.createStartElement(sourceTagName, null, null));
    writeXmlElement(writer, classFile);
    writer.add(eventFactory.createEndElement(sourceTagName, null));

    writer.add(eventFactory.createEndElement(linkageErrorTagName, null));
  }

  private static void writeXmlElement(XMLEventWriter writer, Symbol symbol)
      throws XMLStreamException {
    if (symbol instanceof ClassSymbol) {
      Attribute className = eventFactory.createAttribute("name", symbol.getClassBinaryName());
      StartElement event =
          eventFactory.createStartElement(
              QName.valueOf("Class"), ImmutableList.of(className).iterator(), null);
      writer.add(event);

      writer.add(eventFactory.createEndElement(QName.valueOf("Class"), null));

    } else if (symbol instanceof MethodSymbol) {
      MethodSymbol methodSymbol = (MethodSymbol) symbol;
      Attribute className = eventFactory.createAttribute("className", symbol.getClassBinaryName());
      Attribute methodName = eventFactory.createAttribute("name", methodSymbol.getName());
      QName methodTagName = QName.valueOf("Method");
      StartElement event =
          eventFactory.createStartElement(
              methodTagName, ImmutableList.of(className, methodName).iterator(), null);
      writer.add(event);

      writer.add(eventFactory.createEndElement("", null, "Method"));

    } else if (symbol instanceof FieldSymbol) {
      FieldSymbol fieldSymbol = (FieldSymbol) symbol;
      Attribute className = eventFactory.createAttribute("className", symbol.getClassBinaryName());
      Attribute methodName = eventFactory.createAttribute("name", fieldSymbol.getName());
      QName fieldTagName = QName.valueOf("Field");
      StartElement event =
          eventFactory.createStartElement(
              fieldTagName, ImmutableList.of(className, methodName).iterator(), null);
      writer.add(event);

      writer.add(eventFactory.createEndElement(fieldTagName, null));
    }
  }

  private static void writeXmlElement(XMLEventWriter writer, ClassFile classFile)
      throws XMLStreamException {
    Attribute className = eventFactory.createAttribute("name", classFile.getBinaryName());

    writer.add(
        eventFactory.createStartElement(
            "", null, "Class", ImmutableList.of(className).iterator(), (Iterator) null));
    writer.add(eventFactory.createEndElement("", null, "Class"));
  }

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
        ExclusionFileWriter.class
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
