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

public class DependencyConflictTest {

  @Test
  public void testToString_versionConflict() {
    MethodSymbol methodSymbol =
        new MethodSymbol("java.lang.Object", "equals", "(Ljava/lang/Object;)Z", false);
    Artifact root = new DefaultArtifact("a:b:1");
    Artifact foo = new DefaultArtifact("com.google:foo:1");
    Artifact bar = new DefaultArtifact("com.google:foo:2");

    DependencyPath selectedPath =
        new DependencyPath(root).append(new Dependency(foo, "compile", false));

    DependencyPath unselectedPath =
        new DependencyPath(root).append(new Dependency(bar, "compile", false));

    DependencyConflict dependencyConflict =
        new DependencyConflict(methodSymbol, selectedPath, unselectedPath);

    assertEquals(
        "Dependency conflict: com.google:foo:1 (selected for the class path) does not "
            + "have \""
            + methodSymbol
            + "\" but com.google:foo:2 (unselected) defines it.\n"
            + "  selected: a:b:jar:1 / com.google:foo:1 (compile)\n"
            + "  unselected: a:b:jar:1 / com.google:foo:2 (compile)",
        dependencyConflict.toString());
  }
}
