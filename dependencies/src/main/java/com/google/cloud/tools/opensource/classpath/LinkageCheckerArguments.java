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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Option for {@link LinkageChecker}. To construct an input class path, the checker requires
 * exactly one of the following types of input:
 *
 * <ul>
 *   <li>{@code bom}: a Maven BOM specified by its Maven coordinates
 *   <li>{@code artifacts}: list of Maven artifacts specified by their Maven coordinates
 *   <li>{@code jarFiles}: list of jar files in the filesystem
 * </ul>
 *
 * @see <a href=
 *     "https://github.com/GoogleCloudPlatform/cloud-opensource-java/tree/master/dependencies#input">
 *     Linkage Checker: Input</a>
 */
final class LinkageCheckerArguments {

  private static final Options options = configureOptions();
  private static final HelpFormatter helpFormatter = new HelpFormatter();

  private final CommandLine commandLine;
  private final ImmutableList<String> extraMavenRepositoryUrls;
  private final boolean addMavenCentral;
  private final boolean reportOnlyReachable;
  private final boolean help;

  private ImmutableList<Artifact> cachedArtifacts;
  
  private LinkageCheckerArguments(CommandLine commandLine) {
    this.commandLine = commandLine;
    this.extraMavenRepositoryUrls =
        commandLine.hasOption("m")
            ? ImmutableList.copyOf(commandLine.getOptionValues("m"))
            : ImmutableList.of();

    // this may throw IllegalArgumentException upon validating the syntax
    extraMavenRepositoryUrls.forEach(RepositoryUtility::mavenRepositoryFromUrl);

    this.addMavenCentral = !commandLine.hasOption("nm");
    this.reportOnlyReachable = commandLine.hasOption("r");
    this.help = commandLine.hasOption("h");
  }

  static LinkageCheckerArguments readCommandLine(String... arguments) throws ParseException {
    // TODO is this reentrant? Can we reuse it?
    // https://issues.apache.org/jira/browse/CLI-291
    CommandLineParser parser = new DefaultParser();

    try {
      CommandLine commandLine = parser.parse(options, arguments);
      return new LinkageCheckerArguments(commandLine);
    } catch (IllegalArgumentException ex) {
      throw new ParseException("Invalid URL syntax in Maven repository URL" + ex.getMessage());
    }
  }

  private static Options configureOptions() {
    Options options = new Options();

    OptionGroup inputGroup = new OptionGroup();

    Option bomOption =
        Option.builder("b")
            .longOpt("bom")
            .hasArg()
            .desc("Maven coordinates for a BOM")
            .build();
    inputGroup.addOption(bomOption);

    Option artifactOption =
        Option.builder("a")
            .longOpt("artifacts")
            .hasArgs()
            .valueSeparator(',')
            .desc(
                "Maven coordinates for artifacts (separated by ',')")
            .build();
    inputGroup.addOption(artifactOption);

    Option jarOption =
        Option.builder("j")
            .longOpt("jars")
            .hasArgs()
            .valueSeparator(',')
            .desc("Jar files (separated by ',')")
            .build();
    inputGroup.addOption(jarOption);

    Option repositoryOption =
        Option.builder("m")
            .longOpt("maven-repositories")
            .hasArgs()
            .valueSeparator(',')
            .desc(
                "Maven repository URLs to search for dependencies. "
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

    Option reportOnlyReachable =
        Option.builder("r")
            .longOpt("report-only-reachable")
            .hasArg(false)
            .desc(
                "Report only reachable linkage errors from the classes in the specified BOM or "
                    + "Maven artifacts")
            .build();
    options.addOption(reportOnlyReachable);

    Option help =
        Option.builder("h")
            .longOpt("help")
            .hasArg(false)
            .desc("Show usage instructions")
            .build();
    options.addOption(help);

    Option exclusionFile =
        Option.builder("e")
            .longOpt("exclusion-file")
            .hasArg(true)
            .desc("Exclusion file to filter out linkage errors based on conditions")
            .build();
    options.addOption(exclusionFile);

    Option writeAsExclusionFile =
        Option.builder("o")
            .longOpt("output-linkage-errors-as-exclusion-file")
            .hasArg(true)
            .desc("Outputs linkages errors as exclusion files")
            .build();
    options.addOption(writeAsExclusionFile);

    options.addOptionGroup(inputGroup);
    return options;
  }

  /** Returns a list of artifacts specified in the option of BOM or coordinates list. */
  ImmutableList<Artifact> getArtifacts() throws RepositoryException {
    if (cachedArtifacts != null) {
      return cachedArtifacts;
    }

    if (commandLine.hasOption("b")) {
      String bomCoordinates = commandLine.getOptionValue("b");

      return cachedArtifacts =
          RepositoryUtility.readBom(bomCoordinates, getMavenRepositoryUrls())
              .getManagedDependencies();
    } else if (commandLine.hasOption("a")) {
      // option 'a'
      String[] mavenCoordinatesOption = commandLine.getOptionValues("a");
      return cachedArtifacts =
          Arrays.stream(mavenCoordinatesOption)
              .map(DefaultArtifact::new)
              .collect(toImmutableList());
    } else {
      throw new IllegalArgumentException(
          "The arguments must have option 'a' or 'b' to list Maven artifacts");
    }
  }

  /**
   * Returns class path entries for the absolute paths of the files specified in the JAR file
   * option.
   */
  ImmutableList<ClassPathEntry> getJarFiles() {
    if (commandLine.hasOption("j")) {
      String[] jarFiles = commandLine.getOptionValues("j");
      return Arrays.stream(jarFiles)
          .map(name -> Paths.get(name).toAbsolutePath())
          .map(ClassPathEntry::new)
          .collect(toImmutableList());
    } else {
      throw new IllegalArgumentException("The arguments must have option 'j' to list JAR files");
    }
  }

  ImmutableList<String> getMavenRepositoryUrls() {
    ImmutableList.Builder<String> repositories = ImmutableList.builder();
    repositories.addAll(extraMavenRepositoryUrls);
    if (addMavenCentral) {
      repositories.add(RepositoryUtility.CENTRAL.getUrl());
    }
    return repositories.build();
  }

  boolean getReportOnlyReachable() {
    return reportOnlyReachable;
  }

  boolean needsHelp() {
    return this.help;
  }

  void printHelp() {
    helpFormatter.printHelp(
        "java com.google.cloud.tools.opensource.classpath.LinkageChecker", options);
  }

  boolean hasInput() {
    return commandLine.hasOption("b") || commandLine.hasOption("a") || commandLine.hasOption("j");
  }

  /**
   * Returns the path to exclusion file specified in the argument. If the argument is not specified,
   * {@code null}.
   */
  Path getExclusionFile() {
    if (commandLine.hasOption("e")) {
      return Paths.get(commandLine.getOptionValue("e"));
    }
    return null;
  }

  /**
   * Returns the path to write linkage errors as exclusion file. If the argument is not specified,
   * {@code null}.
   */
  Path getWriteAsExclusionFile() {
    if (commandLine.hasOption("o")) {
      return Paths.get(commandLine.getOptionValue("o"));
    }
    return null;
  }
}
