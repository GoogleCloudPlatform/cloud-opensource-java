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

package com.google.cloud.tools.opensource.dependencies;

import com.google.common.collect.ImmutableList;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

final class DependencyTableArguments {

  private static final Options options = configureOptions();
  private static final HelpFormatter helpFormatter = new HelpFormatter();

  private final ImmutableList<String> bomCoordinates;

  private final ImmutableList<String> artifactCoordinates;

  private final CommandLine commandLine;

  ImmutableList<String> getBomCoordinates() {
    return bomCoordinates;
  }

  ImmutableList<String> getArtifactCoordinates() {
    return artifactCoordinates;
  }

  private DependencyTableArguments(CommandLine commandLine) {
    this.commandLine = commandLine;

    this.bomCoordinates = commandLine.hasOption("b") ?
        ImmutableList.copyOf(commandLine.getOptionValues("b")) : ImmutableList.of();

    this.artifactCoordinates = commandLine.hasOption("a") ?
        ImmutableList.copyOf(commandLine.getOptionValues("a")) : ImmutableList.of();
  }

  static DependencyTableArguments readCommandLine(String... arguments) throws ParseException {
    CommandLineParser parser = new DefaultParser();

    try {
      return new DependencyTableArguments(parser.parse(options, arguments));
    } catch (ParseException ex) {
      helpFormatter.printHelp("DependencyTable", options);
      throw ex;
    }
  }

  private static Options configureOptions() {
    Options options = new Options();

    Option bomOption =
        Option.builder("b")
            .longOpt("boms")
            .hasArgs()
            .valueSeparator(',')
            .desc("BOMs to check their managed dependencies, specified by its Maven coordinates "+"(separator ',')")
            .build();
    options.addOption(bomOption);

    Option artifactOption =
        Option.builder("a")
            .longOpt("artifacts")
            .hasArgs()
            .valueSeparator(',')
            .desc("Maven artifacts to check its dependencies, specified by its Maven coordinates "+"(separator ',')")
            .build();

    options.addOption(artifactOption);
    return options;
  }

}
