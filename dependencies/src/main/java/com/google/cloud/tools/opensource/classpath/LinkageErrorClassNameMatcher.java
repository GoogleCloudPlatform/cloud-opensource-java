package com.google.cloud.tools.opensource.classpath;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

class LinkageErrorClassNameMatcher implements SymbolProblemMatcher {

  @JacksonXmlProperty(localName = "name")
  private String className;

  // For Xml parsing
  private LinkageErrorClassNameMatcher() {}

  LinkageErrorClassNameMatcher(String className) {
    this.className = className;
  }

  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    return sourceClass.getBinaryName().equals(className);
  }
}
