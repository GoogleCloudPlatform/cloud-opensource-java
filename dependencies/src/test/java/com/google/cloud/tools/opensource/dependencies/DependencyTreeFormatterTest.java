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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Test;

public class DependencyTreeFormatterTest {

  @Test
  public void testDependencyTree() {
    ByteArrayOutputStream dummyStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(dummyStream);
    List<DependencyPath> dependencyPathList = new ArrayList<>();
    // Given these 4 artifacts as DependencyPath
    DependencyPath dependency1 = new DependencyPath();
    dependency1.add(new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"));
    dependencyPathList.add(dependency1);

    DependencyPath dependency2 = new DependencyPath();
    dependency2.add(new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"));
    dependency2.add(new DefaultArtifact("io.grpc:grpc-core:jar:1.15.0"));
    dependencyPathList.add(dependency2);

    DependencyPath dependency3 = new DependencyPath();
    dependency3.add(new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"));
    dependency3.add(new DefaultArtifact("io.grpc:grpc-core:jar:1.15.0"));
    dependency3.add(new DefaultArtifact("io.grpc:grpc-context:jar:1.15.0"));
    dependencyPathList.add(dependency3);

    DependencyPath dependency4 = new DependencyPath();
    dependency4.add(new DefaultArtifact("io.grpc:grpc-auth:jar:1.15.0"));
    dependency4.add(new DefaultArtifact("io.grpc:grpc-core:jar:1.15.0"));
    dependency4.add(new DefaultArtifact("com.google.code.gson:gson:jar:2.7"));
    dependencyPathList.add(dependency4);

    DependencyTreeFormatter.printDependencyPaths(dependencyPathList, printStream);
    String outputContent = dummyStream.toString();
    String expectedTreeOutput =
        "  io.grpc:grpc-auth:jar:1.15.0\n"
            + "    io.grpc:grpc-core:jar:1.15.0\n"
            + "      io.grpc:grpc-context:jar:1.15.0\n"
            + "      com.google.code.gson:gson:jar:2.7\n";
    Assert.assertEquals(
        "The dependency should be output as tree with indentation",
        expectedTreeOutput, outputContent);
  }
}
