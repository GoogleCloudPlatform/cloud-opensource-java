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

import com.google.cloud.tools.opensource.classpath.SymbolNotResolvable.Reason;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
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

    RepositoryUtility.setRepositories(linkageCheckerArguments.getExtraMavenRepositoryUrls(),
        linkageCheckerArguments.getAddMavenCentral());

    LinkageChecker linkageChecker = LinkageChecker.create(
        linkageCheckerArguments.getInputClasspath(),
        linkageCheckerArguments.getEntryPointJars());
    LinkageCheckReport report = linkageChecker.findLinkageErrors();

    ImmutableMap<Path, Artifact> pathToArtifact = linkageCheckerArguments.getPathToArtifact();

    for (JarLinkageReport jarLinkageReport : report.getJarLinkageReports()) {
      diagnoseJarLinkageReport(jarLinkageReport, pathToArtifact);
    }
    System.out.println(report);
  }

  private static JarLinkageReport diagnoseJarLinkageReport(
      JarLinkageReport jarLinkageReport, Map<Path, Artifact> pathToArtifact)
      throws RepositoryException, IOException {
    Artifact sourceArtifact = pathToArtifact.get(jarLinkageReport.getJarPath());

    ImmutableMultimap<LinkageErrorCause, String> causeToSourceClasses =
        jarLinkageReport.getCauseToSourceClasses();
    if (causeToSourceClasses.isEmpty()) {
      return jarLinkageReport;
    }

    ImmutableMap<String, Artifact> selectedArtifacts =
        Maps.uniqueIndex(pathToArtifact.values(), Artifacts::makeKey);

    ImmutableMap<Path, Artifact> pathToArtifactForJar =
        ClassPathBuilder.getPathToArtifact(ImmutableList.of(sourceArtifact));

    for (LinkageErrorCause cause : causeToSourceClasses.keySet()) {
      if (cause.getReason() == Reason.CLASS_NOT_FOUND) {
        LinkageErrorDiagnosis diagnosis =
            diagnoseMissingClass(cause, sourceArtifact, selectedArtifacts, pathToArtifactForJar);
        System.out.println(diagnosis);
      }
    }

    return jarLinkageReport;
  }

  private static LinkageErrorDiagnosis diagnoseMissingClass(
      LinkageErrorCause cause,
      Artifact sourceArtifact,
      Map<String, Artifact> selectedArtifacts,
      Map<Path, Artifact> pathToArtifactForJar)
      throws IOException {

    for (Path path : pathToArtifactForJar.keySet()) {
      boolean hasMissingClass =
          ClassDumper.listClassesInJar(path).stream()
              .anyMatch(javaClass -> javaClass.getClassName().equals(cause.getSymbol()));
      if (hasMissingClass) {
        Artifact artifactWithResolvableSymbol = pathToArtifactForJar.get(path);
        String versionLessCoordinates = Artifacts.makeKey(artifactWithResolvableSymbol);
        Artifact selectedArtifact = selectedArtifacts.get(versionLessCoordinates);

        LinkageErrorDiagnosis diagnosis =
            LinkageErrorDiagnosis.builder()
                .setLinkageErrorCause(cause)
                .setSourceArtifact(sourceArtifact)
                .setArtifactInClassPath(selectedArtifact)
                .setArtifactWithResolvableSymbol(artifactWithResolvableSymbol)
                .build();
        return diagnosis;
      }
    }

    LinkageErrorDiagnosis diagnosis =
        LinkageErrorDiagnosis.builder()
            .setLinkageErrorCause(cause)
            .setSourceArtifact(sourceArtifact)
            .setArtifactWithResolvableSymbol(null)
            .setArtifactInClassPath(null)
            .build();
    return diagnosis;
  }
}
