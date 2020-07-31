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

package com.google.cloud.tools.opensource.classpath;

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.testing.EqualsTester;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class LinkageProblemCauseTest {
  @Test
  public void testEquality() {
    Artifact root = new DefaultArtifact("a:b:1");
    Artifact foo = new DefaultArtifact("com.google:foo:1");
    Artifact bar = new DefaultArtifact("com.google:bar:1");

    MethodSymbol methodSymbol =
        new MethodSymbol("java.lang.Object", "equals", "(Ljava/lang/Object;)Z", false);

    ClassSymbol classSymbol = new ClassSymbol("java.lang.Object");

    DependencyPath path1 = new DependencyPath(root).append(new Dependency(foo, "compile", false));

    DependencyPath path2 = new DependencyPath(root).append(new Dependency(bar, "compile", false));

    new EqualsTester()
        .addEqualityGroup(
            new DependencyConflict(methodSymbol, path1, path2),
            new DependencyConflict(methodSymbol, path1, path2))
        .addEqualityGroup(new DependencyConflict(methodSymbol, path1, path1))
        .addEqualityGroup(new DependencyConflict(methodSymbol, path2, path2))
        .addEqualityGroup(new DependencyConflict(classSymbol, path2, path2))
        .addEqualityGroup(new MissingDependency(path1), new MissingDependency(path1))
        .addEqualityGroup(new ExcludedDependency(path1, foo), new ExcludedDependency(path1, foo))
        .addEqualityGroup(new ExcludedDependency(path1, bar))
        .addEqualityGroup(new MissingDependency(path2))
        .addEqualityGroup(UnknownCause.getInstance())
        .testEquals();
  }
}
