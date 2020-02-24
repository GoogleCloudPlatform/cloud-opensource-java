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

import static com.google.cloud.tools.opensource.classpath.ClassPathBuilderTest.PATH_FILE_NAMES;

import com.google.common.truth.Truth;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.junit.Assert;
import org.junit.Test;

public class LinkageCheckerArgumentsTest {

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
  public void testReadCommandLine_multipleJars() throws ParseException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "-j", "/foo/bar/A.jar,/foo/bar/B.jar,/foo/bar/C.jar");

    Truth.assertThat(parsedArguments.getJarFiles())
        .comparingElementsUsing(PATH_FILE_NAMES)
        .containsExactly("A.jar", "B.jar", "C.jar");
  }

  @Test
  public void testGetJarFiles_jarFileList() throws ParseException {

    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("--jars", "dir1/foo.jar,dir2/bar.jar,baz.jar");
    List<Path> inputClasspath = parsedArguments.getJarFiles();

    Truth.assertThat(inputClasspath)
        .comparingElementsUsing(PATH_FILE_NAMES)
        .containsExactly("foo.jar", "bar.jar", "baz.jar");
  }

  @Test
  public void testGetJarFiles_invalidOption() throws ParseException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "--artifacts", "com.google.guava:guava:26.0,io.grpc:grpc-core:1.17.1");

    try {
      parsedArguments.getJarFiles();
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      // pass
      Assert.assertEquals("The arguments must have option 'j' to list JAR files", ex.getMessage());
    }
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
  public void testReadCommandLine_multipleRepositoriesSeparatedByComma() throws ParseException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine(
            "-j", "dummy.jar", "-m", "file:///var/tmp,https://repo.spring.io/milestone");

    // file, Spring, and Maven Central
    Truth.assertThat(parsedArguments.getMavenRepositoryUrls()).hasSize(3);
  }

  @Test
  public void testReadCommandLine_reportOnlyReachableOff() throws ParseException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("-j", "dummy.jar");

    Truth.assertThat(parsedArguments.getReportOnlyReachable()).isFalse();
  }

  @Test
  public void testReadCommandLine_reportOnlyReachableOn() throws ParseException {
    LinkageCheckerArguments parsedArguments =
        LinkageCheckerArguments.readCommandLine("-j", "dummy.jar", "-r");

    Truth.assertThat(parsedArguments.getReportOnlyReachable()).isTrue();
  }
}
