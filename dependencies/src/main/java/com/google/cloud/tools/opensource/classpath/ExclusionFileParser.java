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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
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
 *   <li>A Target element has Package, Class, Method, and Field elements. A Source element has
 *       Artifact, Package, and Class elements.
 *   <li>Method and Field elements have “className” attribute.
 * </ul>
 *
 * <p>Each type of the element works as a corresponding matcher, such as LinkageErrorMatcher for a
 * LinkageError element and SourceMatcher for Source element. Given a linkage error, they work as
 * below:
 *
 * <ul>
 *   <li>A LinkageErrorMatcher matches when all of its child elements match the linkage error.
 *   <li>A SourceMatcher matches a linkage error when the source class of the error matches one of
 *       its child elements.
 *   <li>A TargetMatcher matches a linkage error when the target symbol (class, method, or field) of
 *       the error matches one of its child elements.
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
      throws SAXException, IOException {

    XMLReader xmlReader = XMLReaderFactory.createXMLReader();
    LinkageErrorMatcherHandler handler = new LinkageErrorMatcherHandler();
    xmlReader.setContentHandler(handler);

    InputSource inputSource = new InputSource(new FileInputStream(exclusionFile.toFile()));
    xmlReader.parse(inputSource);
    return handler.getMatchers();
  }

  private static class LinkageErrorMatcherHandler extends DefaultHandler {

    private ArrayDeque<SymbolProblemMatcher> stack = new ArrayDeque<>();
    private ImmutableList.Builder<LinkageErrorMatcher> matchers;

    ImmutableList<LinkageErrorMatcher> getMatchers() {
      return matchers.build();
    }

    @Override
    public void startDocument() {
      matchers = ImmutableList.builder();
    }

    private void addMatcherToTop(Object child) throws SAXException {
      SymbolProblemMatcher parent = stack.peek();
      if (parent instanceof SourceMatcher && child instanceof SymbolProblemSourceMatcher) {
        ((SourceMatcher) parent).addMatcher((SymbolProblemSourceMatcher) child);
      } else if (parent instanceof TargetMatcher && child instanceof SymbolProblemTargetMatcher) {
        ((TargetMatcher) parent).addMatcher((SymbolProblemTargetMatcher) child);
      } else {
        throw new SAXException(
            "Unexpected parent-child relationship. Parent:" + parent + ", child:" + child);
      }
    }

    @Override
    public void startElement(
        String namespaceURI, String localName, String qName, Attributes attributes)
        throws SAXException {
      switch (localName) {
        case "LinkageCheckerFilter":
          break;
        case "LinkageError":
          stack.push(new LinkageErrorMatcher());
          break;
        case "Source":
          SourceMatcher sourceMatcher = new SourceMatcher();
          ((LinkageErrorMatcher) stack.peek()).setSourceMatcher(sourceMatcher);
          stack.push(sourceMatcher);
          break;
        case "Target":
          TargetMatcher targetMatcher = new TargetMatcher();
          ((LinkageErrorMatcher) stack.peek()).setTargetMatcher(targetMatcher);
          stack.push(targetMatcher);
          break;
        case "Package":
          PackageMatcher packageMatcher = new PackageMatcher(attributes.getValue("name"));
          addMatcherToTop(packageMatcher);
          break;
        case "Class":
          String classNameOnClass = attributes.getValue("name");
          ClassMatcher classMatcher = new ClassMatcher(classNameOnClass);
          addMatcherToTop(classMatcher);
          break;
        case "Method":
          String classNameOnMethod = attributes.getValue("className");
          MethodMatcher methodMatcher =
              new MethodMatcher(classNameOnMethod, attributes.getValue("name"));
          addMatcherToTop(methodMatcher);
          break;
        case "Field":
          String classNameOnField = attributes.getValue("className");
          FieldMatcher fieldMatcher =
              new FieldMatcher(classNameOnField, attributes.getValue("name"));
          addMatcherToTop(fieldMatcher);
          break;
        default:
          throw new SAXException("Unknown tag " + localName);
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      SymbolProblemMatcher poppedMatcher;
      switch (localName) {
        case "Source":
        case "Target":
          poppedMatcher = stack.pop();
          if (!(poppedMatcher instanceof TargetMatcher)
              && !(poppedMatcher instanceof SourceMatcher)) {
            throw new SAXException("Unexpected matcher in stack");
          }
          break;
        case "LinkageError":
          poppedMatcher = stack.pop();
          if (!(poppedMatcher instanceof LinkageErrorMatcher)) {
            throw new SAXException("Unexpected stack status after reading LinkageError element");
          }
          matchers.add((LinkageErrorMatcher) poppedMatcher);
      }
    }

    @Override
    public void endDocument() throws SAXException {
      if (stack.size() != 0) {
        throw new SAXException("Unexpected elements in stack");
      }
    }
  }
}
