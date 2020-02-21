/*
 * Copyright 2020 Google LLC.
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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;
import org.junit.Test;

public class CycleBreakerGraphTransformerTest {

  @Test
  public void testCycleBreaking() throws DependencyResolutionException {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    DefaultRepositorySystemSession session =
        RepositoryUtility.createDefaultRepositorySystemSession(system);

    // This dependencySelector selects everything except test scope. This creates a dependency tree
    // with a cycle of dom4j:dom4j:jar:1.6.1 (optional) and jaxen:jaxen:jar:1.1-beta-6 (optional).
    session.setDependencySelector(new ScopeDependencySelector("test"));

    session.setDependencyGraphTransformer(
        new ChainedDependencyGraphTransformer(
            new CycleBreakerGraphTransformer(), // This prevents StackOverflowError
            new JavaDependencyContextRefiner()));

    // dom4j:1.6.1 is known to have a cycle
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency(new DefaultArtifact("dom4j:dom4j:jar:1.6.1"), "compile"));
    DependencyRequest request = new DependencyRequest(collectRequest, null);

    // This should not raise StackOverflowError
    system.resolveDependencies(session, request);
  }
}
