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
import static com.google.cloud.tools.opensource.classpath.StaticLinkageChecker.printStaticLinkageError;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Tool to run StaticLinkageCheck for each artifacts listed in Google Cloud OSS BOM
 */
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
      List<Path> fileNames =
          jarFilePaths.stream().map(p -> p.getFileName()).collect(Collectors.toList());
      System.out.println("Starting to read " + jarFilePaths.size() + " files: \n" + fileNames);
      List<FullyQualifiedMethodSignature> unresolvedMethodReferences =
          findUnresolvedMethodReferences(jarFilePaths);
      if (unresolvedMethodReferences.isEmpty()) {
        System.out.println("There were no unresolved method references from the first jar file :");
        System.out.println(jarFilePaths.get(0).getFileName());
      } else {
        printStaticLinkageError(unresolvedMethodReferences);
      }
    }
  }

}
