/*
 * Copyright 2018 Google LLC.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Option for {@link ClasspathChecker}. To construct an input class path, the checker requires
 * exactly one of the following types of input:
 *
 * <ul>
 *   <li>{@code bom}: a Maven BOM specified by its Maven coordinates
 *   <li>{@code artifacts}: list of Maven artifacts specified by their Maven coordinates
 *   <li>{@code jarFiles}: list of jar files in the filesystem
 * </ul>
 *
 * @see <a href=
 *    "https://github.com/GoogleCloudPlatform/cloud-opensource-java/tree/master/dependencies#input">
 *    Classpath Checker: Input</a>
 */
public class StaticLinkageCheckOption {

  private static final Options options = configureOptions();

  private static final HelpFormatter helpFormatter = new HelpFormatter();

  static CommandLine readCommandLine(String... arguments) throws ParseException {
    // TODO is this reentrant? Can we reuse it?
    // https://issues.apache.org/jira/browse/CLI-291
    CommandLineParser parser = new DefaultParser();

    try {
      CommandLine commandLine = parser.parse(options, arguments);
      checkInput(commandLine);
      return commandLine;
    } catch (ParseException ex) {
      helpFormatter.printHelp("ClasspathChecker", options);
      throw ex;
    }
  }

  private static void checkInput(CommandLine commandLine) throws ParseException {
    if (Stream.of('b', 'a', 'j').filter(commandLine::hasOption).count() > 1) {
      throw new ParseException(
          "Exactly one of BOM, Maven coordinates, or jar files must be specified");
    }
  }

  private static Options configureOptions() {
    Options options = new Options();

    options.addOption(
        "b", "bom", true, "BOM to generate a class path, specified by its Maven coordinates");

    Option artifactOption = Option.builder("a").longOpt("artifacts").hasArgs()
        .valueSeparator(',')
        .desc("Maven coordinates for Maven artifacts (separated by ',') to generate a class path")
        .build();
    options.addOption(artifactOption);

    Option jarOption = Option.builder("j").longOpt("jars").hasArgs()
        .valueSeparator(',')
        .desc("Jar files (separated by ',') to generate a class path")
        .build();
    options.addOption(jarOption);

    options.addOption(
        "r",
        "report-only-reachable",
        false,
        "To report only linkage errors reachable from entry point");

    Option repositoryOption = Option.builder("m").longOpt("maven-repositories").hasArgs()
        .valueSeparator(',')
        .desc("Maven repository URLs to search for dependencies. "
            + "The repositories are added to a repository list in order before "
            + "the default Maven Central (http://repo1.maven.org/maven2/).")
        .build();
    options.addOption(repositoryOption);

    Option noMavenCentralOption =
        Option.builder("nm")
            .longOpt("no-maven-central")
            .hasArg(false)
            .desc(
                "Do not search Maven Central in addition to the repositories specified by -m. "
                    + "Useful when Maven Central is inaccessible.")
            .build();
    options.addOption(noMavenCentralOption);

    return options;
  }

  static ImmutableList<Path> generateInputClasspath(CommandLine commandLine)
      throws RepositoryException, ParseException {
    setRepositories(commandLine);

    if (commandLine.hasOption("b")) {
      String bomCoordinates = commandLine.getOptionValue("b");
      DefaultArtifact bomArtifact = new DefaultArtifact(bomCoordinates);
      List<Artifact> artifactsInBom = RepositoryUtility.readBom(bomArtifact);
      return ClassPathBuilder.artifactsToClasspath(artifactsInBom);
    } else if (commandLine.hasOption("a")) {
      String[] mavenCoordinatesOption = commandLine.getOptionValues("a");
      ImmutableList<Artifact> artifacts =
          Arrays.stream(mavenCoordinatesOption)
              .map(DefaultArtifact::new)
              .collect(toImmutableList());
      return ClassPathBuilder.artifactsToClasspath(artifacts);
    } else if (commandLine.hasOption("j")) {
      String[] jarFiles = commandLine.getOptionValues("j");
      ImmutableList<Path> jarFilesInArguments =
          Arrays.stream(jarFiles)
              .map(name -> Paths.get(name).toAbsolutePath())
              .collect(toImmutableList());
      return jarFilesInArguments;
    } else {
      helpFormatter.printHelp("ClasspathChecker", options);
      throw new ParseException("Missing argument");
    }
  }

  static void setRepositories(CommandLine commandLine) throws ParseException {
    if (!commandLine.hasOption("m")) {
      return;
    }
    try {
      boolean addMavenCentral = !commandLine.hasOption("nm");
      RepositoryUtility.setRepositories(Arrays.asList(commandLine.getOptionValues("m")),
          addMavenCentral);
    } catch (IllegalArgumentException ex) {
      throw new ParseException("Invalid URL specified for Maven repositories: "
          + ex.getMessage());
    }
  }
}
