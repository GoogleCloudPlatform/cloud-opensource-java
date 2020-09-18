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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
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

    ImmutableList.Builder<Artifact> artifactsBuilder = ImmutableList.builder();

    Set<LinkageProblem> intrinsicErrors = new HashSet<>();

    for (String coordinates : arguments) {
      Artifact artifact = new DefaultArtifact(coordinates);

      LinkageChecker linkageChecker;
      if (artifact.getExtension().equals("pom")) {
        Bom bom = Bom.readBom(artifact.toString());
        ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();
        artifactsBuilder.addAll(managedDependencies);

        ClassPathResult classPathResult = classPathResultOf(managedDependencies);
        ImmutableList<ClassPathEntry> classPath = classPathResult.getClassPath();
        linkageChecker = LinkageChecker.create(classPath, classPath.subList(0, managedDependencies.size()), null);
      } else {
        artifactsBuilder.add(artifact);
        ClassPathResult classPathResult = classPathResultOf(ImmutableList.of(artifact));
        ImmutableList<ClassPathEntry> classPath = classPathResult.getClassPath();
        linkageChecker = LinkageChecker.create(classPath, classPath.subList(0, 1), null);
      }

      ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

      linkageProblems = filterReachable(linkageProblems, linkageChecker);
      System.out.println(
          coordinates + " intrinsic problems: " + linkageProblems.size());
      intrinsicErrors.addAll(linkageProblems);
    }

    ImmutableList<Artifact> artifacts = artifactsBuilder.build();
    ClassPathResult classPathResult = classPathResultOf(artifacts);
    ImmutableList<ClassPathEntry> classPath = classPathResult.getClassPath();
    LinkageChecker canaryProjectChecker = LinkageChecker.create(classPath, classPath.subList(0, artifacts.size()), null);

    ImmutableSet<LinkageProblem> canaryProjectErrors=
        canaryProjectChecker.findLinkageProblems();
    canaryProjectErrors = filterReachable(canaryProjectErrors, canaryProjectChecker);

    ImmutableSet<LinkageProblem> canaryOnlyErrors =canaryProjectErrors.stream().filter(problem -> !intrinsicErrors.contains(problem)).collect(toImmutableSet());

    System.out.println(LinkageProblem.formatLinkageProblems(canaryOnlyErrors, classPathResult));
  }

  private static ImmutableSet<LinkageProblem> filterReachable(
      Set<LinkageProblem> problems, LinkageChecker linkageChecker) {
    ClassReferenceGraph classReferenceGraph = linkageChecker.getClassReferenceGraph();

    return problems.stream().filter(problem -> classReferenceGraph.isReachable(problem.getSourceClass().getBinaryName())).collect(toImmutableSet());
  }

  private static ClassPathResult classPathResultOf(List<Artifact> artifacts) throws IOException {

    DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder(
        ImmutableList.of(
            "https://repository.apache.org/content/repositories/snapshots/",
            "https://maven-central.storage-download.googleapis.com/maven2/"
        )
    );
    ClassPathBuilder classPathBuilder = new ClassPathBuilder(dependencyGraphBuilder);
    ClassPathResult classPathResult = classPathBuilder.resolve(artifacts, false);
    if (!classPathResult.getArtifactProblems().isEmpty()) {
      throw new IOException("Couldn't resolve class path: " + classPathResult.getArtifactProblems());
    }

    return classPathResult;
  }
}
