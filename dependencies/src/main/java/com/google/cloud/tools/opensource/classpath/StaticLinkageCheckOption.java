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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@AutoValue
abstract class StaticLinkageCheckOption {
  // TODO(suztomo): Add option to specify entry point classes

  @Nullable abstract String getBomCoordinate();
  abstract ImmutableList<String> getMavenCoordinates();
  abstract ImmutableList<Path> getJarFileList();
  abstract boolean isReportOnlyReachable();

  static Builder builder() {
    return new AutoValue_StaticLinkageCheckOption.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setBomCoordinate(@Nullable String value);
    abstract Builder setMavenCoordinates(Iterable<String> value);
    abstract Builder setJarFileList(Iterable<Path> value);
    abstract Builder setReportOnlyReachable(boolean value);
    abstract StaticLinkageCheckOption build();
  }

  static StaticLinkageCheckOption parseArgument(String[] arguments) {
    Options options = new Options();
    options.addOption(
        "b", "bom", true, "BOM to generate a classpath");
    options.addOption(
        "c", "coordinate", true, "Maven coordinates (separated by ',') to generate a classpath");
    options.addOption("j", "jars", true, "Jar files (separated by ',') to generate a classpath");
    options.addOption(
        "r",
        "report-only-reachable",
        false,
        "To report only linkage errors reachable from entry point");

    CommandLineParser parser = new DefaultParser();
    List<Path> jarFilePaths = new ArrayList<>();

    ImmutableList.Builder<String> mavenCoordinates = ImmutableList.builder();
    try {
      CommandLine cmd = parser.parse(options, arguments);
      if (cmd.hasOption("c")) {
        String mavenCoordinatesOption = cmd.getOptionValue("c");
        mavenCoordinates.addAll(Arrays.asList(mavenCoordinatesOption.split(",")));
      }
      if (cmd.hasOption("j")) {
        String jarFiles = cmd.getOptionValue("j");
        List<Path> jarFilesInArguments =
            Arrays.stream(jarFiles.split(","))
                .map(name -> (Paths.get(name)).toAbsolutePath())
                .collect(Collectors.toList());
        jarFilePaths.addAll(jarFilesInArguments);
      }

      String mavenBomCoordinate = cmd.getOptionValue("b");

      boolean reportOnlyReachable = cmd.hasOption("r");

      return builder()
          .setBomCoordinate(mavenBomCoordinate)
          .setMavenCoordinates(mavenCoordinates.build())
          .setJarFileList(jarFilePaths)
          .setReportOnlyReachable(reportOnlyReachable)
          .build();
    } catch (ParseException ex) {
      throw new IllegalArgumentException("Failed to parse command line arguments",
          ex);
    }
  }
}
