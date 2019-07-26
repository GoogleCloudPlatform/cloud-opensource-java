package com.google.cloud.tools.opensource.dependencies;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;

class NoopDependencyManager implements DependencyManager {

  @Override
  public DependencyManagement manageDependency(Dependency dependency) {
    return null;
  }

  @Override
  public DependencyManager deriveChildManager(DependencyCollectionContext context) {
    return this;
  }
}
