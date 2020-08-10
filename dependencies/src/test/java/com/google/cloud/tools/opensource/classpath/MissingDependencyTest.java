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

import static org.junit.Assert.assertEquals;

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class MissingDependencyTest {

  @Test
  public void testToString_optional() {
    Artifact root = new DefaultArtifact("a:b:1");
    Artifact foo = new DefaultArtifact("com.google:foo:1");
    Artifact bar = new DefaultArtifact("com.google:bar:1");

    DependencyPath pathRootFooBar =
        new DependencyPath(root)
            .append(new Dependency(foo, "test", false))
            .append(new Dependency(bar, "compile", true));

    MissingDependency missingDependency = new MissingDependency(pathRootFooBar);

    assertEquals(
        "The valid symbol is in com.google:bar:jar:1 at a:b:jar:1 / "
            + "com.google:foo:1 (test) / com.google:bar:1 (compile, optional) "
            + "but it was not selected because the path contains an optional dependency",
        missingDependency.toString());
  }

  @Test
  public void testToString_provided() {
    Artifact root = new DefaultArtifact("a:b:1");
    Artifact foo = new DefaultArtifact("com.google:foo:1");
    Artifact bar = new DefaultArtifact("com.google:bar:1");

    DependencyPath pathRootFooBar =
        new DependencyPath(root)
            .append(new Dependency(foo, "test", false))
            .append(new Dependency(bar, "provided", false));

    MissingDependency missingDependency = new MissingDependency(pathRootFooBar);

    assertEquals(
        "The valid symbol is in com.google:bar:jar:1 at a:b:jar:1 / "
            + "com.google:foo:1 (test) / com.google:bar:1 (provided) "
            + "but it was not selected because the path contains a provided-scope dependency",
        missingDependency.toString());
  }
}
