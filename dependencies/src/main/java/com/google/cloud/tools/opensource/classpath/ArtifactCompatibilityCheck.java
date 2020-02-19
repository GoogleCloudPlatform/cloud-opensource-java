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

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

class ArtifactCompatibilityCheck {
  public static void main(String[] arguments) throws IOException, ArtifactDescriptorException {

    if (arguments.length < 2) {
      System.out.println(
          "Please specify 2 or more coordinates.\n"
              + "For example: org.apache.beam:beam-runners-google-cloud-dataflow-java:2.19.0"
              + " com.google.cloud:libraries-bom:pom:4.1.0");
      System.exit(1);
    }

    ImmutableList.Builder<Artifact> artifacts = ImmutableList.builder();

    Set<Entry<SymbolProblem, ClassFile>> intrinsicErrors = new HashSet<>();

    for (String coordinates : arguments) {
      Artifact artifact = new DefaultArtifact(coordinates);
      artifacts.add(artifact);
      LinkageChecker linkageChecker = linkagecheckerFor(artifact);

      ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
          linkageChecker.findSymbolProblems();

      symbolProblems = filterReachable(symbolProblems, linkageChecker);
      System.out.println(coordinates + " intrinsic problems (problem x source pairs): " + symbolProblems.size());
      intrinsicErrors.addAll(symbolProblems.entries());
    }

    LinkageChecker canaryProjectChecker = linkageCheckerOf(artifacts.build());

    ImmutableSetMultimap<SymbolProblem, ClassFile> canaryProjectErrors =
        canaryProjectChecker.findSymbolProblems();
    canaryProjectErrors = filterReachable(canaryProjectErrors, canaryProjectChecker);

    ImmutableSetMultimap<SymbolProblem, ClassFile> canaryOnlyErrors = ImmutableSetMultimap
        .copyOf(
            Multimaps.filterEntries(canaryProjectErrors,
                entry -> !intrinsicErrors.contains(entry)));

    for (SymbolProblem symbolProblem : canaryOnlyErrors.keySet()) {
      ImmutableSet<ClassFile> sourceClasses = canaryOnlyErrors.get(symbolProblem);
      System.out.println(symbolProblem);
      System.out.println("  referenced by");
      for (ClassFile sourceClass : sourceClasses) {
        System.out.println("    " + sourceClass);
      }
    }
  }

  /**
   * Returns a linkage checker for {@code artifact}. If the artifact is a BOM (extension "pom"),
   * then it creates the linkage checker for the class path generated from the artifacts in {@code
   * dependencyManagement} section; otherwise the linkage checker for the class path from the
   * artifact and its {@code dependencies} section.
   */
  static LinkageChecker linkagecheckerFor(Artifact artifact)
      throws IOException, ArtifactDescriptorException {
    if (artifact.getExtension().equals("pom")) {
      Bom bom = RepositoryUtility.readBom(artifact.toString());
      ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();
      return linkageCheckerOf(managedDependencies);
    } else {
      return linkageCheckerOf(ImmutableList.of(artifact));
    }
  }

  static ImmutableSetMultimap<SymbolProblem, ClassFile> filterReachable(
      ImmutableSetMultimap<SymbolProblem, ClassFile> problems,
      LinkageChecker linkageChecker
  ) {
    ClassReferenceGraph classReferenceGraph = linkageChecker.getClassReferenceGraph();
    return ImmutableSetMultimap.copyOf(
        Multimaps.filterValues(problems,
        classFile -> classReferenceGraph.isReachable(classFile.getBinaryName())));
  }

  /**
   * Returns a linkage checker that analyzes the class path generated from {@code artifacts} and
   * their dependencies.
   */
  static LinkageChecker linkageCheckerOf(List<Artifact> artifacts) throws IOException {
    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult classPathResult = classPathBuilder.resolve(artifacts);

    ImmutableList<Path> classPath = classPathResult.getClassPath();
    return LinkageChecker.create(classPath, classPath.subList(0, artifacts.size()));
  }
}
