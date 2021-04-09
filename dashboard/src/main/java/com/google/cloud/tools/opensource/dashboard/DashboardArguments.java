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

package com.google.cloud.tools.opensource.dashboard;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Command-line option for {@link DashboardMain}. The tool takes either a pom.xml file path or Maven
 * coordinates for a BOM.
 */
final class DashboardArguments {
  private static final Options options = configureOptions();
  private static final HelpFormatter helpFormatter = new HelpFormatter();

  private static final ImmutableList<String> validDependencyMediationValues =
      ImmutableList.of("maven", "gradle");

  private final CommandLine commandLine;

  private DashboardArguments(CommandLine commandLine) {
    this.commandLine = commandLine;
  }

  /**
   * Returns true if the argument for a file is specified. False if the argument for coordinates is
   * specified.
   *
   * <p>It is guaranteed that either a file path or Maven coordinates for a BOM are available.
   */
  boolean hasFile() {
    return commandLine.hasOption('f');
  }

  /**
   * Returns true if the argument for a versionless coordinates is specified; otherwise false.
   *
   * <p>It is guaranteed that either a file path or Maven coordinates for a BOM are available.
   */
  boolean hasVersionlessCoordinates() {
    return commandLine.hasOption('a');
  }

  /** Returns an absolute path to pom.xml file of a BOM. Null if file is not specified. */
  @Nullable
  Path getBomFile() {
    if (!commandLine.hasOption('f')) {
      return null;
    }
    // Trim the value so that maven exec plugin can pass arguments with exec.arguments="-f pom.xml"
    return Paths.get(commandLine.getOptionValue('f').trim()).toAbsolutePath();
  }

  /** Returns the Maven coordinates of a BOM. Null if coordinates are not specified. */
  @Nullable
  String getBomCoordinates() {
    if (!commandLine.hasOption('c')) {
      return null;
    }
    return commandLine.getOptionValue('c').trim();
  }

  /**
   * Returns the versionless Maven coordinates of a BOM. Null if versionless coordinates are not
   * specified.
   */
  @Nullable
  String getVersionlessCoordinates() {
    if (!commandLine.hasOption('a')) {
      return null;
    }
    return commandLine.getOptionValue('a').trim();
  }

  static DashboardArguments readCommandLine(String... arguments) throws ParseException {
    CommandLineParser parser = new DefaultParser();

    try {
      // Throws ParseException if required option group ('-f' or '-c') is not specified
      CommandLine commandLine = parser.parse(options, arguments);
      String dependencyMediationValue = commandLine.getOptionValue('m');
      if (dependencyMediationValue != null
          && !validDependencyMediationValues.contains(dependencyMediationValue)) {
        throw new ParseException("Valid values for '-m' are " + validDependencyMediationValues);
      }

      return new DashboardArguments(commandLine);
    } catch (ParseException ex) {
      helpFormatter.printHelp("DashboardMain", options);
      throw ex;
    }
  }

  enum DependencyMediationAlgorithm {
    MAVEN,
    GRADLE,
  }

  /**
   * Returns dependency mediation algorithm. By default it's {@link
   * DependencyMediationAlgorithm#MAVEN}.
   */
  DependencyMediationAlgorithm getDependencyMediation() {
    if (!commandLine.hasOption('m')) {
      return DependencyMediationAlgorithm.MAVEN;
    }
    String optionValue = commandLine.getOptionValue('m').trim();
    return "maven".equals(optionValue)
        ? DependencyMediationAlgorithm.MAVEN
        : DependencyMediationAlgorithm.GRADLE;
  }

  private static Options configureOptions() {
    Options options = new Options();
    OptionGroup inputGroup = new OptionGroup();
    inputGroup.setRequired(true);

    Option inputFileOption =
        Option.builder("f").longOpt("bom-file").hasArg().desc("File to a BOM (pom.xml)").build();
    inputGroup.addOption(inputFileOption);

    Option inputCoordinatesOption =
        Option.builder("c")
            .longOpt("bom-coordinates")
            .hasArg()
            .desc(
                "Maven coordinates of a BOM. For example, com.google.cloud:libraries-bom:1.0.0")
            .build();
    inputGroup.addOption(inputCoordinatesOption);

    Option versionlessCoordinatesOption =
        Option.builder("a")
            .longOpt("all-versions")
            .hasArg()
            .desc(
                "Maven coordinates of a BOM without version. "
                    + "For example, com.google.cloud:libraries-bom")
            .build();
    inputGroup.addOption(versionlessCoordinatesOption);

    Option dependencyMediationOption =
        Option.builder("m")
            .longOpt("dependency-mediation")
            .hasArg()
            .desc(
                "The dependency mediation algorithm to choose versions. The valid values are:\n"
                    + "- 'maven' for nearest-win strategy (default)\n"
                    + "- 'gradle' for highest-win strategy.")
            .build();
    options.addOption(dependencyMediationOption);

    options.addOptionGroup(inputGroup);
    return options;
  }
}
