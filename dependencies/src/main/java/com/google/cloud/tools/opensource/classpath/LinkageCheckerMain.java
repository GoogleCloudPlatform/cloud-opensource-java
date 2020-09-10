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
        // artifactsInArguments is not empty if a BOM or artifacts are specified in the argument.
        // If JAR files are specified, it's empty.
        ImmutableList<Artifact> artifactsInArguments = linkageCheckerArguments.getArtifacts();

        ImmutableSet<LinkageProblem> linkageProblems =
            artifactsInArguments.isEmpty()
                ? checkJarFiles(linkageCheckerArguments)
                : checkArtifacts(linkageCheckerArguments);
        if (!linkageProblems.isEmpty()) {
          System.out.println(
              "For the details of the linkage errors, see "
                  + "https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Messages");
          // Throwing an exception is more test-friendly compared with System.exit(1). The latter
          // abruptly stops test execution.
          throw new LinkageCheckResultException(linkageProblems.size());
        }
      }
    } catch (ParseException ex) {
      System.err.println(ex.getMessage());
    }
  }

  private static ImmutableSet<LinkageProblem> checkJarFiles(
      LinkageCheckerArguments linkageCheckerArguments)
      throws IOException, TransformerException, XMLStreamException {
    ImmutableList<ClassPathEntry> inputClassPath = linkageCheckerArguments.getJarFiles();
    ImmutableSet<ClassPathEntry> entryPoints = ImmutableSet.copyOf(inputClassPath);
    LinkageChecker linkageChecker =
        LinkageChecker.create(
            inputClassPath, entryPoints, linkageCheckerArguments.getInputExclusionFile());
    ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

    linkageProblems = filterReachable(linkageCheckerArguments, linkageChecker, linkageProblems);

    if (writeExclusionFile(linkageCheckerArguments, linkageProblems)) {
      return ImmutableSet.of();
    }

    if (!linkageProblems.isEmpty()) {
      System.out.println(LinkageProblem.formatLinkageProblems(linkageProblems));
    }

    return linkageProblems;
  }

  private static ImmutableSet<LinkageProblem> filterReachable(
      LinkageCheckerArguments linkageCheckerArguments,
      LinkageChecker linkageChecker,
      ImmutableSet<LinkageProblem> linkageProblems) {
    if (linkageCheckerArguments.getReportOnlyReachable()) {
      ClassReferenceGraph graph = linkageChecker.getClassReferenceGraph();
      linkageProblems =
          linkageProblems.stream()
              .filter(
                  (LinkageProblem problem) ->
                      graph.isReachable(problem.getSourceClass().getBinaryName()))
              .collect(toImmutableSet());
    }
    return linkageProblems;
  }

  /**
   * Writes {@code linkageProblems} to a file and returns true if {@code linkageCheckerArguments}
   * has the option; otherwise returns false.
   */
  private static boolean writeExclusionFile(
      LinkageCheckerArguments linkageCheckerArguments, ImmutableSet<LinkageProblem> linkageProblems)
      throws TransformerException, XMLStreamException, IOException {
    Path writeAsExclusionFile = linkageCheckerArguments.getOutputExclusionFile();
    if (writeAsExclusionFile != null) {
      ExclusionFiles.write(writeAsExclusionFile, linkageProblems);
      System.out.println("Wrote the linkage errors as exclusion file: " + writeAsExclusionFile);
      return true;
    }
    return false;
  }

  private static ImmutableSet<LinkageProblem> checkArtifacts(
      LinkageCheckerArguments linkageCheckerArguments)
      throws IOException, RepositoryException, TransformerException, XMLStreamException {
    ImmutableList<Artifact> artifactsInArguments = linkageCheckerArguments.getArtifacts();

    // When a BOM or Maven artifacts are passed as arguments, resolve the dependencies.
    DependencyGraphBuilder dependencyGraphBuilder =
        new DependencyGraphBuilder(linkageCheckerArguments.getMavenRepositoryUrls());
    ClassPathBuilder classPathBuilder = new ClassPathBuilder(dependencyGraphBuilder);
    ClassPathResult classPathResult = classPathBuilder.resolve(artifactsInArguments, false);
    ImmutableList<ClassPathEntry> inputClassPath = classPathResult.getClassPath();
    ImmutableList<ArtifactProblem> artifactProblems =
        ImmutableList.copyOf(classPathResult.getArtifactProblems());
    ImmutableSet<ClassPathEntry> entryPoints =
        ImmutableSet.copyOf(inputClassPath.subList(0, artifactsInArguments.size()));

    LinkageChecker linkageChecker =
        LinkageChecker.create(
            inputClassPath, entryPoints, linkageCheckerArguments.getInputExclusionFile());
    ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

    linkageProblems = filterReachable(linkageCheckerArguments, linkageChecker, linkageProblems);

    LinkageProblemCauseAnnotator.annotate(classPathBuilder, classPathResult, linkageProblems);

    if (writeExclusionFile(linkageCheckerArguments, linkageProblems)) {
      return ImmutableSet.of();
    }

    if (!linkageProblems.isEmpty()) {
      System.out.println(LinkageProblem.formatLinkageProblems(linkageProblems));
    }

    if (!linkageProblems.isEmpty()) {
      ImmutableSet.Builder<ClassPathEntry> problematicJars = ImmutableSet.builder();
      for (LinkageProblem linkageProblem : linkageProblems) {
        ClassFile targetClass = linkageProblem.getTargetClass();
        if (targetClass != null) {
          problematicJars.add(targetClass.getClassPathEntry());
        }
        ClassFile sourceClassFile = linkageProblem.getSourceClass();
        problematicJars.add(sourceClassFile.getClassPathEntry());
      }
      System.out.println(classPathResult.formatDependencyPaths(problematicJars.build()));
    }

    if (!artifactProblems.isEmpty()) {
      System.out.println("\n");
      System.out.println(ArtifactProblem.formatProblems(artifactProblems));
    }
    return linkageProblems;
  }
}
