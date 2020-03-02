package com.google.cloud.tools.opensource.classpath;

class LinkageErrorPackageMatcher implements SymbolProblemMatcher {

  @Override
  public boolean match(SymbolProblem problem, ClassFile sourceClass) {
    return false;
  }
}
