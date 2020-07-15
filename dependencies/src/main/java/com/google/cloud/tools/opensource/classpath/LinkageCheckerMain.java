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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.cloud.tools.opensource.dependencies.ArtifactProblem;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;

/**
 * A tool to find linkage errors for a class path.
 */
class LinkageCheckerMain {

  /**
   * Forms a classpath from Maven coordinates or a list of jar files and reports linkage errors in
   * that classpath.
   *
   * @throws IOException when there is a problem reading a jar file
   * @throws RepositoryException when there is a problem resolving the Maven coordinates to jar
   *     files
   */
  public static void main(String[] arguments)
      throws IOException, RepositoryException, TransformerException, XMLStreamException,
          LinkageCheckResultException {

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
          classPathResult = classPathBuilder.resolve(artifacts, true);
          inputClassPath = classPathResult.getClassPath();
          artifactProblems.addAll(classPathResult.getArtifactProblems());
          entryPoints = ImmutableSet.copyOf(inputClassPath.subList(0, artifacts.size()));
        }

        LinkageChecker linkageChecker =
            LinkageChecker.create(
                inputClassPath, entryPoints, linkageCheckerArguments.getInputExclusionFile());
        ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

        if (linkageCheckerArguments.getReportOnlyReachable()) {
          ClassReferenceGraph graph = linkageChecker.getClassReferenceGraph();
          linkageProblems =
              linkageProblems.stream()
                  .filter(
                      (LinkageProblem problem) ->
                          graph.isReachable(problem.getSourceClass().getBinaryName()))
                  .collect(toImmutableSet());
        }

        if (classPathResult != null) {
          LinkageProblemCauseAnnotator.annotate(classPathResult, linkageProblems);
        }

        Path writeAsExclusionFile = linkageCheckerArguments.getOutputExclusionFile();
        if (writeAsExclusionFile != null) {
          ExclusionFiles.write(writeAsExclusionFile, linkageProblems);
          System.out.println("Wrote the linkage errors as exclusion file: " + writeAsExclusionFile);
          return;
        }

        if (!linkageProblems.isEmpty()) {
          System.out.println(LinkageProblem.formatLinkageProblems(linkageProblems));
        }

        if (classPathResult != null && !linkageProblems.isEmpty()) {
          ImmutableSet.Builder<ClassPathEntry> problematicJars = ImmutableSet.builder();
          for (LinkageProblem linkageProblem : linkageProblems) {
            if (linkageProblem instanceof IncompatibleLinkageProblem) {
              IncompatibleLinkageProblem incompatibleLinkageProblem =
                  (IncompatibleLinkageProblem) linkageProblem;
              problematicJars.add(incompatibleLinkageProblem.getTargetClass().getClassPathEntry());
            }
            ClassFile sourceClassFile = linkageProblem.getSourceClass();
            problematicJars.add(sourceClassFile.getClassPathEntry());
          }
          System.out.println(classPathResult.formatDependencyPaths(problematicJars.build()));
        }
    
        if (!artifactProblems.isEmpty()) {
          System.out.println("\n");
          System.out.println(ArtifactProblem.formatProblems(artifactProblems));
          System.out.println(
              "For the details of the linkage errors, see "
                  + "https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Messages");
        }

        if (!linkageProblems.isEmpty()) {
          // Throwing an exception is more test-friendly compared with System.exit(1). The latter
          // abruptly stops test execution.
          throw new LinkageCheckResultException(linkageProblems.size());
        }
      }
    } catch (ParseException ex) {
      System.err.println(ex.getMessage());
    }
  }
}
