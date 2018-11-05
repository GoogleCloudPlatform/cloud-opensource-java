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

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class StaticLinkageCheckOption {
  private final Optional<ImmutableList<String>> mavenCoordinates;
  private final Optional<ImmutableList<Path>> jarFileList;
  private final boolean reportOnlyReachable;

  public Optional<ImmutableList<String>> getMavenCoordinates() {
    return mavenCoordinates;
  }

  public Optional<ImmutableList<Path>> getJarFileList() {
    return jarFileList;
  }

  public boolean isReportOnlyReachable() {
    return reportOnlyReachable;
  }

  private StaticLinkageCheckOption(Optional<ImmutableList<String>> mavenCoordinates,
      Optional<ImmutableList<Path>> jarFileList, boolean reportOnlyReachable) {
    this.mavenCoordinates = mavenCoordinates;
    this.jarFileList = jarFileList;
    this.reportOnlyReachable = reportOnlyReachable;
  }

  static StaticLinkageCheckOption parseArgument(String[] arguments) {
    Options options = new Options();
    options.addOption(
        "c", "coordinate", true, "Maven coordinates (separated by ',') to generate a classpath");
    options.addOption("j", "jars", true, "Jar files (separated by ',') to generate a classpath");
    options.addOption(
        "r",
        "report-only-reachable",
        false,
        "To report only linkage errors reachable from entry point");

    HelpFormatter formatter = new HelpFormatter();
    CommandLineParser parser = new DefaultParser();
    List<Path> jarFilePaths = new ArrayList<>();

    Optional<ImmutableList<String>> mavenCoordinates = Optional.empty();
    try {
      CommandLine cmd = parser.parse(options, arguments);
      if (cmd.hasOption("c")) {
        String mavenCoordinatesOption = cmd.getOptionValue("c");
        mavenCoordinates = Optional.of(ImmutableList.copyOf(mavenCoordinatesOption.split(",")));
      }
      if (cmd.hasOption("j")) {
        String jarFiles = cmd.getOptionValue("j");
        List<Path> jarFilesInArguments =
            Arrays.stream(jarFiles.split(","))
                .map(name -> (Paths.get(name)).toAbsolutePath())
                .collect(Collectors.toList());
        jarFilePaths.addAll(jarFilesInArguments);
      }
      boolean reportOnlyReachable = cmd.hasOption("r");

      return new StaticLinkageCheckOption(
          mavenCoordinates,
          Optional.of(ImmutableList.copyOf(jarFilePaths)),
          reportOnlyReachable);
    } catch (ParseException ex) {
      System.err.println("Failed to parse command line arguments: " + ex.getMessage());
      throw new IllegalArgumentException();
    }

  }
}
