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

package com.google.cloud.tools.opensource.classpath;

import static com.google.cloud.tools.opensource.classpath.StaticLinkageChecker.findUnresolvedMethodReferences;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

class BOMStaticLinkageChecker {

  public static void main(String[] arguments)
      throws RepositoryException, IOException, ClassNotFoundException {
    DefaultArtifact bom =
        new DefaultArtifact("com.google.cloud:cloud-oss-bom:pom:0.67.0-SNAPSHOT");
    List<Artifact> managedDependencies = RepositoryUtility.readBom(bom);
    for (Artifact artifact : managedDependencies) {
      String coordinate = Artifacts.toCoordinates(artifact);
      List<Path> jarFilePaths = StaticLinkageChecker.parseArguments(new String[]{"-c", coordinate});
      System.out.println("Managed Dependency: " + coordinate);
      List<Path> fileNames = jarFilePaths.stream().map(p -> p.getFileName()).collect(Collectors.toList());
      System.out.println("Starting to read " + jarFilePaths.size() + " files: \n" + fileNames);
      StringBuilder stringBuilder = new StringBuilder();
      List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
          findUnresolvedMethodReferences(jarFilePaths);
      if (unresolvedMethodReferences.isEmpty()) {
        stringBuilder.append("There were no unresolved method references from the first jar file :");
        stringBuilder.append(jarFilePaths.get(0));
      } else {
        int count = unresolvedMethodReferences.size();
        stringBuilder.append(
            "There were " + count + " unresolved method references from the jar file(s):\n");
        for (FullyQualifiedMethodSignature methodReference : unresolvedMethodReferences) {
          stringBuilder.append("Class: '");
          stringBuilder.append(methodReference.getClassName());
          stringBuilder.append("', method: '");
          stringBuilder.append(methodReference.getMethodSignature().getMethodName());
          stringBuilder.append("' with descriptor ");
          stringBuilder.append(methodReference.getMethodSignature().getDescriptor());
          stringBuilder.append("\n");
        }
      }
      stringBuilder.append("\n");
      System.out.println(stringBuilder.toString());

    }
  }

}
