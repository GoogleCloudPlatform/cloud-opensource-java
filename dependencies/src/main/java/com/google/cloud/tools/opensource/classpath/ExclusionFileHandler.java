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
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Handler for Linkage Checker exclusion XML files.
 *
 * <p>An instance of this class can process one document and cannot be reused for other documents.
 */
class ExclusionFileHandler extends DefaultHandler {

  private ImmutableList.Builder<LinkageErrorMatcher> matchers;
  private LinkageErrorMatcher latestLinkageErrorMatcher;
  // Either SourceMatcher or TargetMatcher
  private SymbolProblemMatcher latestSymbolProblemMatcher;

  ImmutableList<LinkageErrorMatcher> getMatchers() {
    return matchers.build();
  }

  @Override
  public void startDocument() {
    if (matchers != null) {
      throw new IllegalStateException("This handler started reading document already");
    }
    matchers = ImmutableList.builder();
  }

  @Override
  public void startElement(
      String namespaceUri, String localName, String qualifiedName, Attributes attributes)
      throws SAXException {
    if (!namespaceUri.isEmpty()) {
      throw new SAXException(
          "unrecognized element: " + qualifiedName + " in namespace " + namespaceUri);
    }
    switch (localName) {
      case "LinkageCheckerFilter":
        break;
      case "LinkageError":
        latestLinkageErrorMatcher = new LinkageErrorMatcher();
        matchers.add(latestLinkageErrorMatcher);
        break;
      case "Source":
        SourceMatcher sourceMatcher = new SourceMatcher();
        latestLinkageErrorMatcher.setSourceMatcher(sourceMatcher);
        latestSymbolProblemMatcher = sourceMatcher;
        break;
      case "Target":
        TargetMatcher targetMatcher = new TargetMatcher();
        latestLinkageErrorMatcher.setTargetMatcher(targetMatcher);
        latestSymbolProblemMatcher = targetMatcher;
        break;
      case "Package":
        latestSymbolProblemMatcher.addChild(new PackageMatcher(attributes.getValue("name")));
        break;
      case "Class":
        String classNameOnClass = attributes.getValue("name");
        latestSymbolProblemMatcher.addChild(new ClassMatcher(classNameOnClass));
        break;
      case "Method":
        String classNameOnMethod = attributes.getValue("className");
        MethodMatcher methodMatcher =
            new MethodMatcher(classNameOnMethod, attributes.getValue("name"));
        if (!(latestSymbolProblemMatcher instanceof TargetMatcher)) {
          throw new SAXException("Unexpected parent-child relationship.");
        }
        latestSymbolProblemMatcher.addChild(methodMatcher);
        break;
      case "Field":
        String classNameOnField = attributes.getValue("className");
        FieldMatcher fieldMatcher = new FieldMatcher(classNameOnField, attributes.getValue("name"));
        if (!(latestSymbolProblemMatcher instanceof TargetMatcher)) {
          throw new SAXException("Unexpected parent-child relationship.");
        }
        latestSymbolProblemMatcher.addChild(fieldMatcher);
        break;
      default:
        throw new SAXException("Unknown tag " + localName);
    }
  }
}
