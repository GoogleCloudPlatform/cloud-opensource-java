package com.google.cloud.tools.opensource.classpath;

class LinkageErrorMatcher implements SymbolProblemMatcher {

  LinkageErrorSourceMatcher sourceMatcher;
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
