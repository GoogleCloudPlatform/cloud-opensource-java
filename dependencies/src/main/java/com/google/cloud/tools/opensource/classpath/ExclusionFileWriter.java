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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

/** Writer for Linkage Checker exclusion files. */
class ExclusionFileWriter {

  static final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

  private static final QName LINKAGE_CHECKER_FILTER_TAG = QName.valueOf("LinkageCheckerFilter");
  private static final QName CLASS_TAG = QName.valueOf("Class");
  private static final QName LINKAGE_ERROR_TAG = QName.valueOf("LinkageError");
  private static final QName TARGET_TAG = QName.valueOf("Target");
  private static final QName SOURCE_TAG = QName.valueOf("Source");
  private static final QName METHOD_TAG = QName.valueOf("Method");
  private static final QName FIELD_TAG = QName.valueOf("Field");

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

      if (System.getProperty("javax.xml.transform.TransformerFactory") == null) {
        try {
          // Prefer Open JDK's default Transformer, rather than the one in net.sf.saxon:Saxon-HE.
          String openJdkDefaultTransformerClassName =
              "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";
          Class.forName(openJdkDefaultTransformerClassName);
          System.setProperty(
              "javax.xml.transform.TransformerFactory", openJdkDefaultTransformerClassName);
        } catch (ClassNotFoundException ex) {
          // If the runtime is not OpenJDK, let Java runtime find available TransformerFactory.
        }
      }
    } finally {
      if (writer != null) {
        writer.close();
      }
    }

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer indentTransformer = transformerFactory.newTransformer();
    indentTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
    // OpenJDK's default Transformer recognizes this property
    indentTransformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    // Add new line character after doctype declaration
    indentTransformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");

    byte[] bytes = buffer.toByteArray();
    System.out.println("bytes length:" + bytes.length);
    System.out.println("XML input: " + new String(bytes));
    ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
    System.out.println("InputStream available: " + inputStream.available());
    StreamSource source = new StreamSource(inputStream);
    try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
      indentTransformer.transform(
          source,
          new StreamResult(outputStream));
    }
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
