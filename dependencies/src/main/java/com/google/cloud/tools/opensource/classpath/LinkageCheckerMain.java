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
   * @throws RepositoryException when there is a problem finding an artifact
   *     in the Maven repository system
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
        // artifacts is not empty if a BOM or Maven coordinates are specified in the argument.
        // If JAR files are specified, it's empty.
        ImmutableList<Artifact> artifacts = linkageCheckerArguments.getArtifacts();

        ImmutableSet<LinkageProblem> linkageProblems =
            artifacts.isEmpty()
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

    ImmutableSet<LinkageProblem> linkageProblems =
        findLinkageProblems(linkageCheckerArguments, linkageChecker);

    if (!linkageProblems.isEmpty()) {
      System.out.println(LinkageProblem.formatLinkageProblems(linkageProblems, null));
    }

    return linkageProblems;
  }

  private static ImmutableSet<LinkageProblem> checkArtifacts(
      LinkageCheckerArguments linkageCheckerArguments)
      throws IOException, RepositoryException, TransformerException, XMLStreamException {
    
    ImmutableList<Artifact> artifacts = linkageCheckerArguments.getArtifacts();

    // When a BOM or Maven artifacts are passed as arguments, resolve the dependencies.
    DependencyGraphBuilder dependencyGraphBuilder =
        new DependencyGraphBuilder(linkageCheckerArguments.getMavenRepositoryUrls());
    ClassPathBuilder classPathBuilder = new ClassPathBuilder(dependencyGraphBuilder);
    ClassPathResult classPathResult = classPathBuilder.resolve(artifacts, false);
    ImmutableList<ClassPathEntry> inputClassPath = classPathResult.getClassPath();
    ImmutableList<ArtifactProblem> artifactProblems =
        ImmutableList.copyOf(classPathResult.getArtifactProblems());
    ImmutableSet<ClassPathEntry> entryPoints =
        ImmutableSet.copyOf(inputClassPath.subList(0, artifacts.size()));

    LinkageChecker linkageChecker =
        LinkageChecker.create(
            inputClassPath, entryPoints, linkageCheckerArguments.getInputExclusionFile());
    ImmutableSet<LinkageProblem> linkageProblems =
        findLinkageProblems(linkageCheckerArguments, linkageChecker);

    LinkageProblemCauseAnnotator.annotate(classPathBuilder, classPathResult, linkageProblems);

    if (!linkageProblems.isEmpty()) {
      System.out.println(LinkageProblem.formatLinkageProblems(linkageProblems, classPathResult));
    }

    if (!artifactProblems.isEmpty()) {
      System.out.println("\n");
      System.out.println(ArtifactProblem.formatProblems(artifactProblems));
    }
    return linkageProblems;
  }

  private static ImmutableSet<LinkageProblem> findLinkageProblems(
      LinkageCheckerArguments linkageCheckerArguments, LinkageChecker linkageChecker)
      throws IOException, TransformerException, XMLStreamException {

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

    // TODO this should be a separate method; not part of findLinkageProblems
    Path outputExclusionFile = linkageCheckerArguments.getOutputExclusionFile();
    if (outputExclusionFile != null) {
      ExclusionFiles.write(outputExclusionFile, linkageProblems);
      System.out.println("Wrote the linkage errors as exclusion file: " + outputExclusionFile);
      
      // TODO why do we return an empty set here?
      return ImmutableSet.of();
    }

    return linkageProblems;
  }
}
