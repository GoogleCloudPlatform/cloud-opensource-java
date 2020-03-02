package com.google.cloud.tools.opensource.classpath;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

class LinkageErrorMatcher implements SymbolProblemMatcher {

  @JacksonXmlProperty(localName = "Source")
  LinkageErrorSourceMatcher sourceMatcher;

  @JacksonXmlProperty(localName = "Target")
  LinkageErrorTargetMatcher targetMatcher;

  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    boolean result = true;
    if (sourceMatcher != null) {
      result &= sourceMatcher.match(problem, sourceClass);
    }
    if (targetMatcher != null) {
      result &= targetMatcher.match(problem, sourceClass);
    }
    return result;
  }
}
