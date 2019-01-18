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
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class StaticLinkageCheckOptionTest {

  @After
  public void cleanup() {
    // Resets the effect of setRepositories
    RepositoryUtility.setRepositories(ImmutableList.of(), true);
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
  public void testReadCommandLine_multipleArtifacts() throws ParseException {
    String[] arguments = {
        "--artifacts", "abc.com:abc:1.1,abc.com:abc-util:1.2",
        "--report-only-reachable"
    };
    CommandLine commandLine = StaticLinkageCheckOption.readCommandLine(arguments);
    Truth.assertThat(commandLine.getOptionValues("a")).hasLength(2);
  }

  @Test
  public void testReadCommandLine_multipleJars() throws ParseException {
    String[] arguments = {
        "-j", "/foo/bar/A.jar,/foo/bar/B.jar,/foo/bar/C.jar",
    };
    CommandLine commandLine = StaticLinkageCheckOption.readCommandLine(arguments);
    Truth.assertThat(commandLine.getOptionValues("j")).hasLength(3);
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
            "-m", "https://repo.spring.io/milestone");
    StaticLinkageCheckOption.setRepositories(commandLine);

    // This artifact does not exist in Maven central, but it is in Spring's repository
    // Spring-asm is used here because it does not have complex dependencies
    Artifact artifact = new DefaultArtifact("org.springframework:spring-asm:3.1.0.RC2");

    List<Path> paths = ClassPathBuilder.artifactsToClasspath(ImmutableList.of(artifact));
    Truth.assertThat(paths).isNotEmpty();
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_notToUseMavenCentral()
      throws ParseException {
    CommandLine commandLine =
        StaticLinkageCheckOption.readCommandLine(
            "-m", "https://repo.spring.io/milestone", "--no-maven-central");
    StaticLinkageCheckOption.setRepositories(commandLine);

    CollectRequest collectRequest = new CollectRequest();
    RepositoryUtility.addRepositoriesToRequest(collectRequest);

    List<RemoteRepository> actualRepositories = collectRequest.getRepositories();
    Truth.assertThat(actualRepositories).hasSize(1);
    Truth.assertThat(actualRepositories.get(0).getHost()).isEqualTo("repo.spring.io");
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_invalidRepositoryUrl()
      throws ParseException {
    assertMavenRepositoryIsInvalid("foobar");
    assertMavenRepositoryIsInvalid("_http_file__https");
    assertMavenRepositoryIsInvalid("localhost/abc");
    assertMavenRepositoryIsInvalid("http://foo^bar");
  }

  private static void assertMavenRepositoryIsInvalid(String repositoryUrl)
      throws ParseException {
    CommandLine commandLine =
        StaticLinkageCheckOption.readCommandLine("-m", repositoryUrl);
    try {
      StaticLinkageCheckOption.setRepositories(commandLine);
      Assert.fail("URL " + repositoryUrl + " should be invalidated");
    } catch (ParseException ex) {
      // pass
    }
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_fileRepositoryUrl() throws ParseException {
    CommandLine commandLine =
        StaticLinkageCheckOption.readCommandLine("-m", "file:///var/tmp");

    // This method should not raise an exception
    StaticLinkageCheckOption.setRepositories(commandLine);
  }

  @Test
  public void testReadCommandLine_multipleRepositoriesSeparatedByComma() throws ParseException {
    CommandLine commandLine =
        StaticLinkageCheckOption.readCommandLine(
            "-m", "file:///var/tmp,https://repo.spring.io/milestone");
    Truth.assertThat(commandLine.getOptionValues("m")).hasLength(2);
  }
}
