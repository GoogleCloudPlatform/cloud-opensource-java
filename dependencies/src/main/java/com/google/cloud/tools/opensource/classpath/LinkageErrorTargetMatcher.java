package com.google.cloud.tools.opensource.classpath;

class LinkageErrorTargetMatcher implements SymbolProblemMatcher {

  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    return true;
  }
}
