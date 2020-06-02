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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;

class ArtifactPairCompatibilityCheck {
  public static void main(String[] arguments) throws IOException, ArtifactDescriptorException {

    if (arguments.length != 2) {
      System.out.println(
          "Please specify 2 coordinates. One for the artifact and the second for a BOM.\n"
              + "For example:"
              + " org.apache.beam:beam-runners-google-cloud-dataflow-java:2.21.0-SNAPSHOT"
              + " com.google.cloud:libraries-bom:pom:4.2.0");
      System.exit(1);
    }

    Artifact artifact = new DefaultArtifact(arguments[0]);
    Bom bom = Bom.readBom(arguments[1]);
    if (bom.getManagedDependencies().isEmpty()) {
      System.out.println("Please specify a BOM in the second argument.");
      System.exit(1);
    }

    LinkageChecker artifactChecker = linkageCheckerOf(ImmutableList.of(artifact));
    ImmutableSetMultimap<SymbolProblem, ClassFile> artifactInherentProblems =
        filterReachable(artifactChecker.findSymbolProblems(), artifactChecker);

    int conflictingArtifactCount = 0;
    int nonConflictingArtifactCount = 0;
    for (Artifact bomMember : bom.getManagedDependencies()) {
      if (!bomMember.getArtifactId().startsWith("google-cloud")) {
        // Focusing google-cloud-XXXX libraries
        continue;
      }
      Set<Entry<SymbolProblem, ClassFile>> intrinsicErrors =
          new HashSet<>(artifactInherentProblems.entries());
      LinkageChecker bomMemberChecker = linkageCheckerOf(ImmutableList.of(bomMember));

      ImmutableSetMultimap<SymbolProblem, ClassFile> bomMemberInherentProblems =
          filterReachable(bomMemberChecker.findSymbolProblems(), bomMemberChecker);
      intrinsicErrors.addAll(bomMemberInherentProblems.entries());

      LinkageChecker canaryProjectChecker = linkageCheckerOf(ImmutableList.of(artifact, bomMember));

      ImmutableSetMultimap<SymbolProblem, ClassFile> canaryProjectProblems =
          filterReachable(canaryProjectChecker.findSymbolProblems(), canaryProjectChecker);

      ImmutableSetMultimap<SymbolProblem, ClassFile> canaryOnlyErrors =
          ImmutableSetMultimap.copyOf(
              Multimaps.filterEntries(
                  canaryProjectProblems, entry -> !intrinsicErrors.contains(entry)));

      System.out.println(
          String.format(
              "### Pair (%s, %s) generated %d symbol problems",
              artifact.toString(), bomMember.toString(), canaryOnlyErrors.keySet().size()));
      if (canaryOnlyErrors.isEmpty()) {
        nonConflictingArtifactCount++;
      } else {
        conflictingArtifactCount++;
      }
      System.out.println(
          String.format(
              "### Conflicting: %d, Non-conflicting: %d",
              conflictingArtifactCount, nonConflictingArtifactCount));

      for (SymbolProblem symbolProblem : canaryOnlyErrors.keySet()) {
        ImmutableSet<ClassFile> sourceClasses = canaryOnlyErrors.get(symbolProblem);
        System.out.println(symbolProblem);
        System.out.println("  referenced by");
        for (ClassFile sourceClass : sourceClasses) {
          System.out.println("    " + sourceClass);
        }
      }
    }
  }

  private static ImmutableSetMultimap<SymbolProblem, ClassFile> filterReachable(
      ImmutableSetMultimap<SymbolProblem, ClassFile> problems, LinkageChecker linkageChecker) {
    ClassReferenceGraph classReferenceGraph = linkageChecker.getClassReferenceGraph();
    return ImmutableSetMultimap.copyOf(
        Multimaps.filterValues(
            problems, classFile -> classReferenceGraph.isReachable(classFile.getBinaryName())));
  }

  /**
   * Returns a linkage checker that analyzes the class path generated from {@code artifacts} and
   * their dependencies.
   */
  private static LinkageChecker linkageCheckerOf(List<Artifact> artifacts) throws IOException {
    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult classPathResult = classPathBuilder.resolve(artifacts);

    ImmutableList<ClassPathEntry> classPath = classPathResult.getClassPath();
    return LinkageChecker.create(classPath, classPath.subList(0, artifacts.size()), null);
  }
}
