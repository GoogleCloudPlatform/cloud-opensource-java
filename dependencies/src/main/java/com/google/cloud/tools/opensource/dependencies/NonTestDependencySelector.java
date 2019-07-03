package com.google.cloud.tools.opensource.dependencies;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

/**
 * Selects dependencies except {@code test} scope.
 *
 * <p>{@link org.eclipse.aether.util.graph.selector.ScopeDependencySelector} has similar capability
 * but it selects all direct dependencies regardless of their scope.
 */
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
