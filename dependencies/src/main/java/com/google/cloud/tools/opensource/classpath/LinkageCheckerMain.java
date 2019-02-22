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

package com.google.cloud.tools.opensource.classpath;

import java.io.IOException;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;

import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;

/**
 * A tool to find static linkage errors for a class path.
 */
class LinkageCheckerMain {

  /**
   * Given Maven coordinates or list of the jar files as file names in filesystem, outputs the
   * report of classpath check.
   *
   * @throws IOException when there is a problem in reading a jar file
   * @throws RepositoryException when there is a problem in resolving the Maven coordinates to jar
   *     files
   * @throws ParseException when the arguments are invalid for the tool
   */
  public static void main(String[] arguments)
      throws IOException, RepositoryException, ParseException {

    ClasspathCheckerArguments linkageCheckerArguments =
        ClasspathCheckerArguments.readCommandLine(arguments);

    RepositoryUtility.setRepositories(linkageCheckerArguments.getExtraMavenRepositoryUrls(),
        linkageCheckerArguments.getAddMavenCentral());

    ClasspathChecker classpathChecker = ClasspathChecker.create(
        linkageCheckerArguments.getInputClasspath(),
        linkageCheckerArguments.getEntryPointJars());
    ClasspathCheckReport report = classpathChecker.findLinkageErrors();

    System.out.println(report);
  }

}
