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
import com.thaiopensource.util.SinglePropertyMap;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.xml.sax.DraconianErrorHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
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

    File exclusion = exclusionFile.toFile();
    validate(exclusion);

    XMLReader xmlReader = XMLReaderFactory.createXMLReader();
    ExclusionFileHandler handler = new ExclusionFileHandler();
    xmlReader.setContentHandler(handler);

    InputSource inputSource = new InputSource(new FileInputStream(exclusion));
    xmlReader.parse(inputSource);
    return handler.getMatchers();
  }

  private static void validate(File file) throws IOException, SAXException {
    ValidationDriver validationDriver =
        new ValidationDriver(
            SinglePropertyMap.newInstance(
                // DraconianErrorHandler throws SAXException upon invalid structure
                ValidateProperty.ERROR_HANDLER, new DraconianErrorHandler()));
    InputStream schema =
        ExclusionFileParser.class
            .getClassLoader()
            .getResourceAsStream("linkage-checker-exclusion-relax-ng.xml");
    validationDriver.loadSchema(new InputSource(schema));
    validationDriver.validate(ValidationDriver.fileInputSource(file));
  }
}
