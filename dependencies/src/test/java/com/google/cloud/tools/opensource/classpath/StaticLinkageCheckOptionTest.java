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

import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class StaticLinkageCheckOptionTest {

  @After
  public void cleanup() {
    // Resets the effect of configureMavenRepositories
    RepositoryUtility.mavenRepositories = ImmutableList.of(RepositoryUtility.CENTRAL);
  }

  @Test
  public void parseCommandLineOptions_shortOptions_bom() throws ParseException {
    String[] arguments = {"-b", "abc.com:dummy:1.2", "-r"};
    CommandLine parsedOption = StaticLinkageCheckOption.readCommandLine(arguments);

    Assert.assertEquals("abc.com:dummy:1.2", parsedOption.getOptionObject('b'));
  }

  @Test
  public void parseCommandLineOptions_duplicates() {
    String[] arguments = {
        "--artifacts", "abc.com:abc:1.1,abc.com:abc-util:1.2",
        "-b", "abc.com:dummy:1.2",
        "--report-only-reachable"
    };
    try {
      StaticLinkageCheckOption.readCommandLine(arguments);
      Assert.fail();
    } catch (ParseException ex) {
      Assert.assertEquals(
          "Exactly one of BOM, Maven coordinates, or jar files must be specified",
          ex.getMessage());
    }
  }

  @Test
  public void parseCommandLineOptions_invalidOption() {
    String[] arguments = {"-x"}; // No such option
    try {
      StaticLinkageCheckOption.readCommandLine(arguments);
      Assert.fail();
    } catch (ParseException ex) {
      Assert.assertEquals("Unrecognized option: -x", ex.getMessage());
    }
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_addingSpringRepository()
      throws ParseException, RepositoryException {
    CommandLine commandLine =
        StaticLinkageCheckOption.readCommandLine(
            new String[] {"-m", "https://repo.spring.io/milestone"});
    StaticLinkageCheckOption.configureMavenRepositories(commandLine);

    // This artifact does not exist in Maven central, but it is in Spring's repository
    // Spring-asm is used here because it does not have complex dependencies
    Artifact artifact = new DefaultArtifact("org.springframework:spring-asm:3.1.0.RC2");

    List<Path> paths = ClassPathBuilder.artifactsToClasspath(ImmutableList.of(artifact));
    Truth.assertThat(paths).isNotEmpty();
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_invalidRepositoryUrl()
      throws ParseException {
    CommandLine commandLine =
        StaticLinkageCheckOption.readCommandLine(
            new String[] {"-m", "https://repo.spring.io/milestone", "-m", "foobar"});

    try {
      StaticLinkageCheckOption.configureMavenRepositories(commandLine);
      Assert.fail();
    } catch (ParseException ex) {
      // pass
      Truth.assertThat(ex.getMessage())
          .isEqualTo("Invalid URL specified for maven repository: foobar");
    }
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_fileRepositoryUrl() throws ParseException {
    CommandLine commandLine =
        StaticLinkageCheckOption.readCommandLine(new String[] {"-m", "file:///var/tmp"});

    // This method should not raise exception
    StaticLinkageCheckOption.configureMavenRepositories(commandLine);
  }
}
