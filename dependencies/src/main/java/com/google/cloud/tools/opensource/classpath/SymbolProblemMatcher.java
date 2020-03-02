package com.google.cloud.tools.opensource.classpath;

public interface SymbolProblemMatcher {

  boolean match(SymbolProblem problem, ClassFile sourceClass);

}
