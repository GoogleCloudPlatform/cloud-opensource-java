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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
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
  private ImmutableList<Path> cachedInputClasspath;
  private ImmutableList<Artifact> cachedArtifacts;
  private final ImmutableList<String> extraMavenRepositoryUrls;
  private final boolean addMavenCentral;

  private LinkageCheckerArguments(CommandLine commandLine) {
    this.commandLine = checkNotNull(commandLine);
    this.extraMavenRepositoryUrls =
        commandLine.hasOption("m")
            ? ImmutableList.copyOf(commandLine.getOptionValues("m"))
            : ImmutableList.of();

    // this may throw IllegalArgumentException upon validating the syntax
    extraMavenRepositoryUrls.forEach(RepositoryUtility::mavenRepositoryFromUrl);

    this.addMavenCentral = !commandLine.hasOption("nm");
  }

  static LinkageCheckerArguments readCommandLine(String... arguments) throws ParseException {
    // TODO is this reentrant? Can we reuse it?
    // https://issues.apache.org/jira/browse/CLI-291
    CommandLineParser parser = new DefaultParser();

    try {
      return new LinkageCheckerArguments(parser.parse(options, arguments));
    } catch (IllegalArgumentException ex) {
      throw new ParseException("Invalid URL syntax in Maven repository URL");
    } catch (ParseException ex) {
      helpFormatter.printHelp("LinkageChecker", options);
      throw ex;
    }
  }

  private static Options configureOptions() {
    Options options = new Options();

    OptionGroup inputGroup = new OptionGroup();
    inputGroup.setRequired(true);

    Option bomOption =
        Option.builder("b")
            .longOpt("bom")
            .hasArg()
            .desc("BOM to generate a class path, specified by its Maven coordinates")
            .build();
    inputGroup.addOption(bomOption);

    Option artifactOption =
        Option.builder("a")
            .longOpt("artifacts")
            .hasArgs()
            .valueSeparator(',')
            .desc(
                "Maven coordinates for Maven artifacts (separated by ',') to generate a class path")
            .build();
    inputGroup.addOption(artifactOption);

    Option jarOption =
        Option.builder("j")
            .longOpt("jars")
            .hasArgs()
            .valueSeparator(',')
            .desc("Jar files (separated by ',') to generate a class path")
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
      return cachedArtifacts = RepositoryUtility.readBom(bomCoordinates).getManagedDependencies();
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
   * Returns a list of absolute paths to jar files for the option. The list includes dependencies if
   * BOM or Maven coordinates are specified in the option.
   */
  ImmutableList<Path> getInputClasspath() throws RepositoryException {
    if (cachedInputClasspath != null) {
      // Avoid unnecessary dependency resolution, which Cloud OSS BOM takes around 4 seconds
      return cachedInputClasspath;
    }

    if (commandLine.hasOption("b") || commandLine.hasOption("a")) {
      List<Artifact> artifacts = getArtifacts();
      cachedInputClasspath = ClassPathBuilder.artifactsToClasspath(artifacts);
    } else {
      // b, a, or j is specified in OptionGroup
      String[] jarFiles = commandLine.getOptionValues("j");
      cachedInputClasspath =
          Arrays.stream(jarFiles)
              .map(name -> Paths.get(name).toAbsolutePath())
              .collect(toImmutableList());
    }
    return cachedInputClasspath;
  }

  /** Returns a set of jar files that hold entry point classes. */
  ImmutableSet<Path> getEntryPointJars() throws RepositoryException {
    if (commandLine.hasOption("a") || commandLine.hasOption('b')) {
      // For an artifact list (or a BOM), the first elements in inputClasspath are the artifacts
      // specified the list, followed by their dependencies.
      int artifactCount = getArtifacts().size();
      // For Maven artifact list (or a BOM), entry point classes are ones in the list
      return ImmutableSet.copyOf(getInputClasspath().subList(0, artifactCount));
    } else {
      // For list of jar files, entry point classes are all classes in the files
      return ImmutableSet.copyOf(getInputClasspath());
    }
  }

  ImmutableList<String> getExtraMavenRepositoryUrls() {
    return extraMavenRepositoryUrls;
  }

  boolean getAddMavenCentral() {
    return addMavenCentral;
  }
}
