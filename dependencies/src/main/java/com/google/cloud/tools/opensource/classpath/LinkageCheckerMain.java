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
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.ImmutableSetMultimap;
import java.io.IOException;
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
   * Forms a classpath from Maven coordinates or a list of jar files
   * and reports linkage errors in that classpath.
   *
   * @throws IOException when there is a problem reading a jar file
   * @throws RepositoryException when there is a problem resolving the Maven coordinates to jar
   *     files
   */
  public static void main(String[] arguments) throws IOException, RepositoryException {

    try {
      LinkageCheckerArguments linkageCheckerArguments =
          LinkageCheckerArguments.readCommandLine(arguments);
      
      if (linkageCheckerArguments.needsHelp() || arguments.length == 0) {
        linkageCheckerArguments.printHelp();
      }

      if (linkageCheckerArguments.hasInput()) { 
        // This is non-empty if a BOM or artifacts are specified in the argument
        ImmutableList<Artifact> artifacts = linkageCheckerArguments.getArtifacts();

        // When JAR files are specified in the argument, artifacts are empty.
        ImmutableList<ClassPathEntry> inputClassPath;
        ImmutableSet<ClassPathEntry> entryPoints;
        List<ArtifactProblem> artifactProblems = new ArrayList<>();
        // classPathResult is kept null if JAR files are specified in the argument
        ClassPathResult classPathResult = null;
    
        // FIXME the if here isn't reachable
        if (artifacts.isEmpty()) {
          // When JAR files are passed as arguments, classPathResult is null, because there is no need
          // to resolve Maven dependencies.
          inputClassPath = linkageCheckerArguments.getJarFiles();
          entryPoints = ImmutableSet.copyOf(inputClassPath);
        } else {
          // When a BOM or Maven artifacts are passed as arguments, resolve the dependencies.
          DependencyGraphBuilder dependencyGraphBuilder =
              new DependencyGraphBuilder(linkageCheckerArguments.getMavenRepositoryUrls());
          ClassPathBuilder classPathBuilder = new ClassPathBuilder(dependencyGraphBuilder);
          classPathResult = classPathBuilder.resolve(artifacts);
          inputClassPath = classPathResult.getClassPath();
          artifactProblems.addAll(classPathResult.getArtifactProblems());
          entryPoints = ImmutableSet.copyOf(inputClassPath.subList(0, artifacts.size()));
        }

        LinkageCheckRequest.Builder request = LinkageCheckRequest.builder(inputClassPath);
        request.exclusionFile(linkageCheckerArguments.getExclusionFile());
        if (linkageCheckerArguments.getReportOnlyReachable()) {
          request.reportOnlyReachable(entryPoints);
        }

        ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
            LinkageChecker.check(request.build());

        System.out.println(SymbolProblem.formatSymbolProblems(symbolProblems));
    
        if (classPathResult != null && !symbolProblems.isEmpty()) {
          Builder<ClassPathEntry> problematicJars = ImmutableSet.builder();
          for (SymbolProblem symbolProblem : symbolProblems.keySet()) {
            ClassFile containingClass = symbolProblem.getContainingClass();
            if (containingClass != null) {
              problematicJars.add(containingClass.getClassPathEntry());
            }
            for (ClassFile classFile : symbolProblems.get(symbolProblem)) {
              problematicJars.add(classFile.getClassPathEntry());
            }
          }
          System.out.println(classPathResult.formatDependencyPaths(problematicJars.build()));
        }
    
        if (!artifactProblems.isEmpty()) {
          System.out.println("\n");
          System.out.println(ArtifactProblem.formatProblems(artifactProblems));
        }
      }
    } catch (ParseException ex) {
      System.err.println(ex.getMessage());
    }
  }
}
