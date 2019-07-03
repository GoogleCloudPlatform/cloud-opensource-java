package com.google.cloud.tools.opensource.dependencies;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

public class NonTestDependencySelector implements DependencySelector {
  @Override
  public boolean selectDependency(Dependency dependency) {
    return !"test".equals(dependency.getScope());
  }

  @Override
  public DependencySelector deriveChildSelector(
      DependencyCollectionContext dependencyCollectionContext) {
    return this;
  }
}
