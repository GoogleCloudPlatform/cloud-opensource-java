/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.opensource.dependencies;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

/** Selects direct provided-scope dependencies. */
public class DirectProvidedDependencySelector implements DependencySelector {

  private int depth;

  DirectProvidedDependencySelector() {
    this(0);
  }

  private DirectProvidedDependencySelector(int depth) {
    this.depth = depth;
  }

  @Override
  public boolean selectDependency(Dependency dependency) {
    if (depth < 2) {
      return true;
    }

    return !"provided".equals(dependency.getScope());
  }

  @Override
  public DependencySelector deriveChildSelector(
      DependencyCollectionContext dependencyCollectionContext) {
    if (depth >= 2) {
      return this;
    }
    return new DirectProvidedDependencySelector(depth + 1);
  }
}
