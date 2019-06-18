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
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class LinkageCheckerArgumentsTest {

  @After
  public void cleanup() {
    // Resets the effect of setRepositories
    RepositoryUtility.setRepositories(ImmutableList.of(), true);
  }

  @Test
  public void parseCommandLineArguments_shortOptions_bom()
      throws ParseException, RepositoryException {
    // This should not raise exception
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("-b", "abc.com:dummy:1.2");

    Truth.assertThat(parsedArguments.getArtifacts()).isEmpty();
  }

  @Test
  public void parseCommandLineArguments_duplicates() {
    try {
      LinkageCheckerArguments.readCommandLine(
          "--artifacts", "abc.com:abc:1.1,abc.com:abc-util:1.2", "-b", "abc.com:dummy:1.2");
      Assert.fail();
    } catch (ParseException ex) {
      Assert.assertEquals(
          "The option 'b' was specified but an option from this group has already been"
              + " selected: 'a'",
          ex.getMessage());
    }
  }

  @Test
  public void testReadCommandLine_multipleArtifacts() throws ParseException, RepositoryException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "--artifacts", "com.google.guava:guava:26.0,io.grpc:grpc-core:1.17.1");
    Truth.assertThat(parsedArguments.getArtifacts()).hasSize(2);
  }

  @Test
  public void testReadCommandLine_multipleJars() throws ParseException, RepositoryException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "-j", "/foo/bar/A.jar,/foo/bar/B.jar,/foo/bar/C.jar");
    Truth.assertThat(parsedArguments.getInputClasspath()).hasSize(3);
  }

  @Test
  public void parseCommandLineArguments_invalidOption() {
    try {
      LinkageCheckerArguments.readCommandLine("-x"); // No such option
      Assert.fail();
    } catch (ParseException ex) {
      Assert.assertEquals("Unrecognized option: -x", ex.getMessage());
    }
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_addingGoogleAndroidRepository()
      throws ParseException, RepositoryException {
    // Previously this test was using https://repo.spring.io/milestone and artifact
    // org.springframework:spring-asm:3.1.0.RC2 but the repository was not stable.
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "-j", "dummy.jar", "-m", "https://dl.google.com/dl/android/maven2");
    RepositoryUtility.setRepositories(
        parsedArguments.getExtraMavenRepositoryUrls(), parsedArguments.getAddMavenCentral());

    // This artifact does not exist in Maven central, but it is in Spring's repository
    // Spring-asm is used here because it does not have complex dependencies
    Artifact artifact = new DefaultArtifact("androidx.lifecycle:lifecycle-common-java8:2.0.0");

    List<Path> paths = ClassPathBuilder.artifactsToClasspath(ImmutableList.of(artifact));
    Truth.assertThat(paths).isNotEmpty();
  }


  @Test
  public void testConfigureAdditionalMavenRepositories_notToUseMavenCentral()
      throws ParseException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "-j", "dummy.jar", "-m", "https://repo.spring.io/milestone", "--no-maven-central");
    RepositoryUtility.setRepositories(
        parsedArguments.getExtraMavenRepositoryUrls(), parsedArguments.getAddMavenCentral());

    CollectRequest collectRequest = new CollectRequest();
    RepositoryUtility.addRepositoriesToRequest(collectRequest);

    List<RemoteRepository> actualRepositories = collectRequest.getRepositories();
    Truth.assertThat(actualRepositories).hasSize(1);
    Truth.assertThat(actualRepositories.get(0).getHost()).isEqualTo("repo.spring.io");
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_invalidRepositoryUrl() {
    assertMavenRepositoryIsInvalid("foobar");
    assertMavenRepositoryIsInvalid("_http_file__https");
    assertMavenRepositoryIsInvalid("localhost/abc");
    assertMavenRepositoryIsInvalid("http://foo^bar");
  }

  private static void assertMavenRepositoryIsInvalid(String repositoryUrl) {
    try {
      LinkageCheckerArguments.readCommandLine("-j", "dummy.jar", "-m", repositoryUrl);
      Assert.fail("URL " + repositoryUrl + " should be invalidated");
    } catch (ParseException ex) {
      // pass
    }
  }

  @Test
  public void testConfigureAdditionalMavenRepositories_fileRepositoryUrl() throws ParseException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("-j", "dummy.jar", "-m", "file:///var/tmp");

    // This method should not raise an exception
    RepositoryUtility.setRepositories(
        parsedArguments.getExtraMavenRepositoryUrls(), parsedArguments.getAddMavenCentral());
  }

  @Test
  public void testReadCommandLine_multipleRepositoriesSeparatedByComma() throws ParseException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "-j", "dummy.jar", "-m", "file:///var/tmp,https://repo.spring.io/milestone");

    Truth.assertThat(parsedArguments.getExtraMavenRepositoryUrls()).hasSize(2);
  }
}
