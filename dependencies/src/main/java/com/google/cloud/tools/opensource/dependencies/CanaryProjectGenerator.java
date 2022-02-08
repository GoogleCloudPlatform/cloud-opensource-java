/*
 * Copyright 2022 Google LLC.
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

import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import org.eclipse.aether.artifact.Artifact;

public class CanaryProjectGenerator {

  public static void main(String[] arguments) throws MavenRepositoryException {

    Bom bom = Bom.readBom(Paths.get(arguments[0]));

    ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();
    StringBuilder output = new StringBuilder("  <dependencies>\n");
    for (Artifact managedDependency : managedDependencies) {
      String groupId = managedDependency.getGroupId();
      String artifactId = managedDependency.getArtifactId();
      if (groupId.startsWith("com.google")
          && (artifactId.startsWith("grpc-") || artifactId.startsWith("proto-"))) {
        // These artifacts are part of the dependencies of main artifacts.
        continue;
      }
      output.append("    <dependency>\n");
      output.append("      <groupId>" + groupId + "</groupId>\n");
      output.append("      <artifactId>" + artifactId + "</artifactId>\n");
      output.append("    </dependency>\n");
    }
    output.append("  </dependencies>");

    System.out.println(output.toString());
  }
}
