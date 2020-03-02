package com.google.cloud.tools.opensource.classpath;

class LinkageErrorClassMatcher implements SymbolProblemMatcher {

  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    return true;
  }
}
