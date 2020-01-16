/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.opensource.dependencies.ArtifactProblem;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;

/**
 * A tool to find linkage errors for a class path.
 */
class LinkageCheckerMain {

  /**
   * Given Maven coordinates or list of the jar files as file names in filesystem, outputs the
   * report of linkage check.
   *
   * @throws IOException when there is a problem in reading a jar file
   * @throws RepositoryException when there is a problem in resolving the Maven coordinates to jar
   *     files
   * @throws ParseException when the arguments are invalid for the tool
   */
  public static void main(String[] arguments)
      throws IOException, RepositoryException, ParseException {

    LinkageCheckerArguments linkageCheckerArguments =
        LinkageCheckerArguments.readCommandLine(arguments);

    DependencyGraphBuilder dependencyGraphBuilder =
        new DependencyGraphBuilder(linkageCheckerArguments.getMavenRepositoryUrls());

    ClassPathBuilder classPathBuilder = new ClassPathBuilder(dependencyGraphBuilder);
    ImmutableList<Artifact> artifacts = linkageCheckerArguments.getArtifacts();

    // When JAR files are specified in the argument, artifacts are empty.
    ImmutableList<Path> inputClassPath;
    List<ArtifactProblem> artifactProblems = new ArrayList<>();
    if (artifacts.isEmpty()) {
      inputClassPath = linkageCheckerArguments.getInputClasspath();
    } else {
      ClassPathResult classPathResult = classPathBuilder.resolve(artifacts);
      inputClassPath = classPathResult.getClassPath();
      artifactProblems.addAll(classPathResult.getArtifactProblems());
    }

    ImmutableSet<Path> entryPointJars = linkageCheckerArguments.getEntryPointJars();
    LinkageChecker linkageChecker = LinkageChecker.create(inputClassPath, entryPointJars);
    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.findSymbolProblems();

    if (linkageCheckerArguments.getReportOnlyReachable()) {
      ClassReferenceGraph graph = linkageChecker.getClassReferenceGraph();
      symbolProblems =
          ImmutableSetMultimap.copyOf(
              Multimaps.filterValues(
                  symbolProblems, classFile -> graph.isReachable(classFile.getClassName())));
    }

    System.out.println(SymbolProblem.formatSymbolProblems(symbolProblems));

    if (!artifactProblems.isEmpty()) {
      System.out.println("\n");
      for (ArtifactProblem artifactProblem : artifactProblems) {
        System.out.println(artifactProblem);
      }
    }
  }
}
