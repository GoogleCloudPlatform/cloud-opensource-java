/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.opensource.lts;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.Uninterruptibles;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Runs the repositories' tests specified in the {@code testCase} with the libraries in the LTS BOM
 * in the class path.
 */
class LtsCompatibilityChecker {
  private static final Logger logger = Logger.getLogger(LtsCompatibilityChecker.class.getName());

  private final RepositoryTestCase testCase;

  LtsCompatibilityChecker(RepositoryTestCase testCase) {
    this.testCase = testCase;
  }

  void run(Bom bom, Path testRoot)
      throws IOException, InterruptedException, TestSetupFailureException, TestFailureException {
    String name = testCase.getName();
    String commands = testCase.getCommands();

    URL url = testCase.getGitUrl();
    // Example: "/grpc/grpc-java.git"
    String urlPath = url.getPath();
    // Example: "grpc-java.git"
    String secondPathElement = urlPath.split("/")[2];
    String projectDirectoryName = secondPathElement.replace(".git", "");
    Path projectDirectory = testRoot.resolve(projectDirectoryName);

    String gitTag = testCase.getGitTag();
    logger.info(name + ": " + url + " at " + gitTag);

    File gitOutput = testRoot.resolve("lts_test_git.log").toFile();
    Process gitProcess =
        new ProcessBuilder("git", "clone", "-b", gitTag, "--depth=1", url.toString())
            .directory(testRoot.toFile())
            .redirectErrorStream(true)
            .redirectOutput(gitOutput)
            .start();

    int checkoutStatusCode = gitProcess.waitFor();

    if (checkoutStatusCode != 0) {
      String outputContent =
          com.google.common.io.Files.asCharSource(gitOutput, Charsets.UTF_8).read();
      logger.severe("Failed to checkout the repository:\n" + outputContent);
      throw new TestFailureException("Could not checkout the Git URL: " + url);
    }
    logger.info("Successfully checked out the repository at " + projectDirectory);

    // Modify build files to use the LTS BOM when they run tests.
    Modification modification = testCase.getModification();
    BuildFileModifier modifier = modification.getModifier();
    modifier.modifyFiles(name, projectDirectory, bom);

    // Build the project, following the "commands" field
    Path shellScript = projectDirectory.resolve("lts_test.sh");
    String shellScriptLocation = shellScript.toAbsolutePath().toString();
    com.google.common.io.Files.asCharSink(shellScript.toFile(), Charsets.UTF_8).write(commands);

    logger.info("Running the commands");

    File output = projectDirectory.resolve("lts_test.log").toFile();

    // "-e" to fail on errors
    Process bashProcess =
        new ProcessBuilder("/bin/bash", "-e", shellScriptLocation)
            .directory(projectDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output)
            .start();

    int buildStatusCode = bashProcess.waitFor();

    String outputContent = com.google.common.io.Files.asCharSource(output, Charsets.UTF_8).read();
    logger.severe("Output:\n" + outputContent);

    // Avoid messing up the log with the output and the exception
    Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(1));

    if (buildStatusCode != 0) {
      throw new TestFailureException("Failed to run the commands.");
    } else {
      logger.info(name + " passed.");
    }
  }
}
