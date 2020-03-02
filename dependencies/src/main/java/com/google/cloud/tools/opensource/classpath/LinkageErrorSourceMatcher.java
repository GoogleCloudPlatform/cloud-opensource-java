package com.google.cloud.tools.opensource.classpath;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

class LinkageErrorSourceMatcher implements SymbolProblemMatcher {

  @JacksonXmlProperty(localName = "Class")
  LinkageErrorClassNameMatcher classNameMatcher;

  void setClassNameMatcher(
      LinkageErrorClassNameMatcher classNameMatcher) {
    this.classNameMatcher = classNameMatcher;
  }

  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    return true;
  }
}
