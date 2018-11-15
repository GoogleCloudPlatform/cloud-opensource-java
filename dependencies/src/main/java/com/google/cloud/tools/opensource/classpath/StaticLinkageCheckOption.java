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
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

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
 * @see <a
 *     href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/tree/master/dependencies#input">Static
 *     Linkage Checker: Input</a>
 */
@AutoValue
abstract class StaticLinkageCheckOption {
  // TODO(suztomo): Add option to specify entry point classes

  /**
   * Returns the Maven coordinates for a BOM if specified; otherwise null. Example value: {@code
   * com.google.cloud:cloud-oss-bom:pom:1.0.0-SNAPSHOT}
   */
  @Nullable
  abstract String getBom();

  /**
   * Returns list of the coordinates of (non-BOM) Maven artifacts if specified; otherwise an empty
   * list. Example element: {@code com.google.cloud:google-cloud-bigtable:0.66.0-alpha}
   */
  abstract ImmutableList<String> getArtifacts();

  /**
   * Returns absolute paths for jar files in the filesystem if specified; otherwise an empty list.
   */
  abstract ImmutableList<Path> getJarFiles();

  /**
   * Returns {@code true} if only reachable linkage errors should be reported.
   */
  abstract boolean isReportOnlyReachable();

  static Builder builder() {
    return new AutoValue_StaticLinkageCheckOption.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setBom(@Nullable String coordinates);
    abstract Builder setArtifacts(List<String> coordinates);
    abstract Builder setJarFiles(List<Path> paths);
    abstract Builder setReportOnlyReachable(boolean value);
    abstract StaticLinkageCheckOption build();
  }

  static StaticLinkageCheckOption parseArguments(String[] arguments) throws ParseException {
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

    CommandLineParser parser = new DefaultParser();
    List<Path> jarFilePaths = new ArrayList<>();

    ImmutableList.Builder<String> mavenCoordinates = ImmutableList.builder();
    try {
      CommandLine commandLine = parser.parse(options, arguments);
      if ((commandLine.hasOption("b") && commandLine.hasOption("a"))
          || (commandLine.hasOption("a") && commandLine.hasOption("j"))
          || (commandLine.hasOption("j") && commandLine.hasOption("b"))) {
        throw new IllegalArgumentException(
            "One of BOM, Maven coordinates, or jar files can be specified");
      }
      if (commandLine.hasOption("a")) {
        String mavenCoordinatesOption = commandLine.getOptionValue("a");
        mavenCoordinates.addAll(Arrays.asList(mavenCoordinatesOption.split(",")));
      }
      if (commandLine.hasOption("j")) {
        String jarFiles = commandLine.getOptionValue("j");
        List<Path> jarFilesInArguments =
            Arrays.stream(jarFiles.split(","))
                .map(name -> (Paths.get(name)).toAbsolutePath())
                .collect(Collectors.toList());
        jarFilePaths.addAll(jarFilesInArguments);
      }

      String mavenBomCoordinates = commandLine.getOptionValue("b");

      boolean reportOnlyReachable = commandLine.hasOption("r");

      return builder()
          .setBom(mavenBomCoordinates)
          .setArtifacts(mavenCoordinates.build())
          .setJarFiles(jarFilePaths)
          .setReportOnlyReachable(reportOnlyReachable)
          .build();
    } catch (ParseException ex) {
      HelpFormatter helpFormatter = new HelpFormatter();
      helpFormatter.printHelp("StaticLinkageChecker", options);
      throw ex;
    }
  }
}
