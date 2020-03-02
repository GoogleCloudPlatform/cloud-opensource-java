package com.google.cloud.tools.opensource.classpath;

class LinkageErrorSourceMatcher implements SymbolProblemMatcher {

  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    return true;
  }
}
