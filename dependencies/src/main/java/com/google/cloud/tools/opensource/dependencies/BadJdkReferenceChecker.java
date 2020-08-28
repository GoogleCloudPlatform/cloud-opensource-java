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

package com.google.cloud.tools.opensource.dependencies;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.LinkageProblem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import org.eclipse.aether.artifact.Artifact;

public class BadJdkReferenceChecker {

  private static final Logger logger = Logger.getLogger(BadJdkReferenceChecker.class.getName());

  public static void main(String[] arguments)
      throws MavenRepositoryException, IOException, URISyntaxException {

    if (arguments.length != 1) {
      System.err.println("Please specify a path to the BOM file");
      System.exit(1);
    }

    URI exclusionFileUri =
        BadJdkReferenceChecker.class
            .getClassLoader()
            .getResource("bad-jdk-reference-check-exclusion.xml")
            .toURI();
    Path exclusionFile = Paths.get(exclusionFileUri).toAbsolutePath();

    String bomFileName = arguments[0];

    Path bomFile = Paths.get(bomFileName);
    Bom bom = Bom.readBom(bomFile);

    ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();

    int count = 1;

    ImmutableSetMultimap.Builder<Artifact, Artifact> badDependencies =
        ImmutableSetMultimap.builder();
    for (Artifact managedDependency : managedDependencies) {
      logger.info(
          "Checking "
              + managedDependency
              + " ("
              + (count++)
              + "/"
              + managedDependencies.size()
              + ")");

      ClassPathBuilder classPathBuilder = new ClassPathBuilder();
      ClassPathResult result = classPathBuilder.resolve(ImmutableList.of(managedDependency), false);

      LinkageChecker linkageChecker =
          LinkageChecker.create(result.getClassPath(), result.getClassPath(), exclusionFile);

      ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

      ImmutableSet<LinkageProblem> badJdkReferences =
          linkageProblems.stream()
              .filter(problem -> problem.getSymbol().getClassBinaryName().startsWith("java."))
              .collect(toImmutableSet());

      if (!badJdkReferences.isEmpty()) {
        badJdkReferences.stream()
            .map(LinkageProblem::getSourceClass)
            .map(ClassFile::getClassPathEntry)
            .map(ClassPathEntry::getArtifact)
            .forEach(artifact -> badDependencies.put(managedDependency, artifact));

        logger.severe(LinkageProblem.formatLinkageProblems(badJdkReferences));
      }
    }

    ImmutableSetMultimap<Artifact, Artifact> bomMemberToBadDependencies = badDependencies.build();

    if (bomMemberToBadDependencies.isEmpty()) {
      logger.info("No problematic artifacts");
      return;
    }

    StringBuilder message = new StringBuilder();
    message.append(
        "The following artifacts contain bad references to classes in 'java' package,"
            + " which does not work for Java 8\n");
    for (Artifact artifact : bomMemberToBadDependencies.inverse().keySet()) {
      message.append("  " + artifact + "\n");
    }
    message.append(
        "The following artifacts in the BOM contain the bad artifacts in their dependencies\n");
    for (Artifact bomMember : bomMemberToBadDependencies.keySet()) {
      ImmutableSet<Artifact> dependencies = bomMemberToBadDependencies.get(bomMember);
      message.append("  " + bomMember + " due to " + dependencies + "\n");
    }
    logger.severe(message.toString());
    System.exit(1);
  }
}
