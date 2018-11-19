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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Option for {@link StaticLinkageChecker}. To construct an input class path, the checker requires
 * exactly one of the following types of input:
 *
 * <ul>
 *   <li>{@code bom}: a Maven BOM specified by its Maven coordinates
 *   <li>{@code artifacts}: list of Maven artifacts specified by their Maven coordinates
 *   <li>{@code jarFiles}: list of jar files in the filesystem
 * </ul>
 *
 * @see <a href=
 *    "https://github.com/GoogleCloudPlatform/cloud-opensource-java/tree/master/dependencies#input">Static
 *     Linkage Checker: Input</a>
 */
class StaticLinkageCheckOption {
  
  private static final Options options = configureOptions();
  
  static CommandLine readCommandLine(String[] arguments) throws ParseException {
    // TODO is this reentrant? Can we reuse it? 
    // https://issues.apache.org/jira/browse/CLI-291
    CommandLineParser parser = new DefaultParser();

    try {
      CommandLine commandLine = parser.parse(options, arguments);
      checkInput(commandLine);
      return commandLine;
    } catch (ParseException ex) {
      HelpFormatter helpFormatter = new HelpFormatter();
      helpFormatter.printHelp("StaticLinkageChecker", options);
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
    options.addOption(
        "a",
        "artifacts",
        true,
        "Maven coordinates for Maven artifacts (separated by ',') to generate a class path");
    options.addOption("j", "jars", true, "Jar files (separated by ',') to generate a class path");
    options.addOption(
        "r",
        "report-only-reachable",
        false,
        "To report only linkage errors reachable from entry point");
    return options;
  }

  static ImmutableList<Path> generateInputClasspath(CommandLine commandLine)
      throws RepositoryException, ParseException {
        
    Splitter commaSplitter = Splitter.on(",");
    if (commandLine.hasOption("a")) {
      ImmutableList.Builder<Path> jarFileBuilder = ImmutableList.builder();
      String mavenCoordinatesOption = commandLine.getOptionValue("a");
      for (String coord : commaSplitter.split(mavenCoordinatesOption)) {
        jarFileBuilder.addAll(StaticLinkageChecker.coordinatesToClasspath(coord));
      }
      return jarFileBuilder.build();
    } else if (commandLine.hasOption("j")) {
      String jarFiles = commandLine.getOptionValue("j");
      ImmutableList<Path> jarFilesInArguments =
          Streams.stream(commaSplitter.split(jarFiles))
              .map(name -> Paths.get(name).toAbsolutePath())
              .collect(ImmutableList.toImmutableList());
      return jarFilesInArguments;
    } else if (commandLine.hasOption("b")) {
      String mavenBomCoordinates = commandLine.getOptionValue("b");
      ImmutableList.Builder<Path> jarFileBuilder = ImmutableList.builder();
      // TODO(suztomo): add logic to convert Maven BOM to list of Maven coordinates as per README.md
      
      return jarFileBuilder.build();
    } else {
      throw new ParseException("Missing argument");
    }
  }
}
