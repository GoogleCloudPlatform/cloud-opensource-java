/*
 * Copyright 2018 Google LLC.
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

import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Assert;
import org.junit.Test;

public class DependencyTreeFormatterTest {

  @Test
  public void testDependencyTree() {
    List<DependencyPath> dependencyPathList = new ArrayList<>();

    // 4 artifacts as DependencyPath dummy inputs

    DependencyPath path2 =
        new DependencyPath(new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"))
            .append(
                new Dependency(
                    new DefaultArtifact("io.grpc:grpc-core:jar:1.15.0"), "compile", false));
    dependencyPathList.add(path2);

    // dependency1 and dependency2 are intentionally added in wrong order
    // formatDependencyPaths is responsible for sorting the items in the tree
    DependencyPath path1 = new DependencyPath(new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"));
    dependencyPathList.add(path1);

    DependencyPath path3 =
        new DependencyPath(new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"))
            .append(
                new Dependency(
                    new DefaultArtifact("io.grpc:grpc-core:jar:1.15.0"), "compile", false))
            .append(
                new Dependency(
                    new DefaultArtifact("io.grpc:grpc-context:jar:1.15.0"), "compile", false));
    dependencyPathList.add(path3);

    DependencyPath path4 =
        new DependencyPath(new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"))
            .append(
                new Dependency(
                    new DefaultArtifact("io.grpc:grpc-core:jar:1.15.0"), "compile", false))
            .append(
                new Dependency(
                    new DefaultArtifact("com.google.code.gson:gson:jar:2.7"), "compile", false));
    dependencyPathList.add(path4);

    String actualTreeOutput = DependencyTreeFormatter.formatDependencyPaths(dependencyPathList);
    String expectedTreeOutput =
        "  io.grpc:grpc-auth:jar:1.15.0\n"
            + "    io.grpc:grpc-core:jar:1.15.0\n"
            + "      io.grpc:grpc-context:jar:1.15.0\n"
            + "      com.google.code.gson:gson:jar:2.7\n";
    Assert.assertEquals(
        "The dependency should be output as tree with indentation",
        expectedTreeOutput, actualTreeOutput);
  }

  @Test
  public void testDependencyTree_scopeAndOptionalFlag() {
    List<DependencyPath> dependencyPathList = new ArrayList<>();

    DependencyPath path1 =
        new DependencyPath(null)
            .append(
                new Dependency(
                    new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"), "compile", true));
    dependencyPathList.add(path1);

    DependencyPath path2 =
        new DependencyPath(null)
            .append(
                new Dependency(
                    new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"), "compile", true))
            .append(
                new Dependency(
                    new DefaultArtifact("io.grpc:grpc-core:jar:1.15.0"), "provided", false));
    dependencyPathList.add(path2);

    String actualTreeOutput = DependencyTreeFormatter.formatDependencyPaths(dependencyPathList);
    String expectedTreeOutput =
        "  io.grpc:grpc-auth:jar:1.15.0\n" + "    io.grpc:grpc-core:jar:1.15.0\n";
    Assert.assertEquals(expectedTreeOutput, actualTreeOutput);
  }
}
