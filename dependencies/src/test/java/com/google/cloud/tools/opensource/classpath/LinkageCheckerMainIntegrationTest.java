/*
 * Copyright 2020 Google LLC.
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

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.truth.Truth;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import org.eclipse.aether.RepositoryException;
import org.hamcrest.core.StringStartsWith;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class LinkageCheckerMainIntegrationTest {

  private static final PrintStream originalStandardOut = System.out;
  private ByteArrayOutputStream capturedOutputStream;

  @Before
  public void setup() {
    capturedOutputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOutputStream));
  }

  @After
  public void cleanup() {
    System.setOut(originalStandardOut);
  }

  private String readCapturedStdout() {
    System.out.flush();
    String output = new String(capturedOutputStream.toByteArray());
    return output;
  }

  @Test
  public void testJarFiles()
      throws IOException, URISyntaxException, RepositoryException, TransformerException,
          XMLStreamException {

    Path googleCloudCore = absolutePathOfResource("testdata/google-cloud-core-1.48.0.jar");
    Path googleCloudFirestore =
        absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar");
    Path guava = absolutePathOfResource("testdata/guava-23.5-jre.jar");

    String jarArgument = googleCloudCore + "," + googleCloudFirestore + "," + guava;

    try {
      LinkageCheckerMain.main(new String[] {"-j", jarArgument});
      fail("LinkageCheckerMain should throw LinkageCheckResultException upon errors");
    } catch (LinkageCheckResultException expected) {
      assertEquals("Found 369 linkage errors", expected.getMessage());
    }

    // Gax is not in the JAR list
    Truth.assertThat(readCapturedStdout())
        .contains(
            "Class com.google.api.gax.retrying.ResultRetryAlgorithm is not found;\n"
                + "  referenced by 1 class file\n"
                + "    com.google.cloud.ExceptionHandler ("
                + googleCloudCore
                + ")");
  }

  @Test
  public void testArtifacts()
      throws IOException, RepositoryException, TransformerException, XMLStreamException {
    try {
      LinkageCheckerMain.main(
          // google-http-client-appengine depends on appengien-api-1.0-sdk which shades its
          // dependencies, resulting in many linkage errors.
          new String[] {"-a", "com.google.http-client:google-http-client-appengine:1.39.2"});
      fail("LinkageCheckerMain should throw LinkageCheckResultException upon errors");
    } catch (LinkageCheckResultException expected) {
      int expectedErrorCount = System.getProperty("java.version").startsWith("1.8.") ? 562 : 584;
      assertEquals("Found " + expectedErrorCount + " linkage errors", expected.getMessage());
    }

    String output = readCapturedStdout();
    Truth.assertThat(output)
        .contains(
            "Class com.google.net.rpc3.client.RpcStubDescriptor is not found;\n"
                + "  referenced by 21 class files\n"
                + "    com.google.appengine.api.appidentity.AppIdentityServicePb"
                + " (com.google.appengine:appengine-api-1.0-sdk:1.9.71)");

    // Show the dependency path to the problematic artifact
    Truth.assertThat(output)
        .contains(
            "com.google.appengine:appengine-api-1.0-sdk:1.9.71 is at:\n"
                + "  com.google.http-client:google-http-client-appengine:jar:1.39.2 /"
                + " com.google.appengine:appengine-api-1.0-sdk:1.9.71 (provided)\n");
  }

  @Test
  public void testArtifacts_noError()
      throws IOException, RepositoryException, TransformerException, XMLStreamException,
          LinkageCheckResultException {
    // Guava does not have any linkage errors
    LinkageCheckerMain.main(new String[] {"-a", "com.google.guava:guava:29.0-jre"});

    String output = readCapturedStdout();
    Truth.assertThat(output).isEmpty();
  }

  @Test
  public void testBom_java8()
      throws IOException, RepositoryException, TransformerException, XMLStreamException {
    // The number of linkage errors differ between Java 8 and Java 11 runtime.
    Assume.assumeThat(System.getProperty("java.version"), StringStartsWith.startsWith("1.8."));

    try {
      LinkageCheckerMain.main(new String[] {"-b", "com.google.cloud:libraries-bom:1.0.0"});
      fail("LinkageCheckerMain should throw LinkageCheckResultException upon errors");
    } catch (LinkageCheckResultException expected) {
      assertEquals("Found 734 linkage errors", expected.getMessage());
    }

    String output = readCapturedStdout();

    // Appengine-api-sdk is known to have invalid references
    Truth.assertThat(output)
        .contains(
            "Class com.google.net.rpc3.client.RpcStubDescriptor is not found;\n"
                + "  referenced by 21 class files\n"
                + "    com.google.appengine.api.appidentity.AppIdentityServicePb"
                + " (com.google.appengine:appengine-api-1.0-sdk:1.9.71)");

    // Show the dependency path to the problematic artifact
    Truth.assertThat(output)
        .contains(
            "com.google.appengine:appengine-api-1.0-sdk:1.9.71 is at:\n"
                + "  com.google.http-client:google-http-client-appengine:1.29.1 (compile) "
                + "/ com.google.appengine:appengine-api-1.0-sdk:1.9.71 (provided)");
  }

  @Test
  // Error: expected:<Found 75[6] linkage errors> but was:<Found 75[8] linkage errors>
  // No previous list of 756 expected linkage errors exists to compare against the new error count.
  public void testBom_java11()
      throws IOException, RepositoryException, TransformerException, XMLStreamException {
    // The number of linkage errors differs between Java 8 and Java 11 runtime.
    String javaVersion = System.getProperty("java.version");
    // javaMajorVersion is 1 when we use Java 8. Still good indicator to ensure Java 11 or higher.
    int javaMajorVersion = Integer.parseInt(javaVersion.split("\\.")[0]);
    Assume.assumeTrue(javaMajorVersion >= 11);

    try {
      LinkageCheckerMain.main(new String[] {"-b", "com.google.cloud:libraries-bom:1.0.0"});
      fail("LinkageCheckerMain should throw LinkageCheckResultException upon errors");
    } catch (LinkageCheckResultException expected) {
      assertEquals("Found 758 linkage errors", expected.getMessage());
    }

    String output = readCapturedStdout();

    // Appengine-api-sdk is known to have invalid references.
    Truth.assertThat(output)
        .contains(
            "Class com.google.net.rpc3.client.RpcStubDescriptor is not found;\n"
                + "  referenced by 21 class files\n"
                + "    com.google.appengine.api.appidentity.AppIdentityServicePb"
                + " (com.google.appengine:appengine-api-1.0-sdk:1.9.71)");

    // javax.activation.DataSource has been removed in Java 11.
    Truth.assertThat(output)
        .contains(
            "Class javax.activation.DataSource is not found;\n"
                + "  referenced by 9 class files\n"
                + "    com.google.appengine.api.utils.HttpRequestParser"
                + " (com.google.appengine:appengine-api-1.0-sdk:1.9.71)");

    // Show the dependency path to the problematic artifact
    Truth.assertThat(output)
        .contains(
            "com.google.appengine:appengine-api-1.0-sdk:1.9.71 is at:\n"
                + "  com.google.http-client:google-http-client-appengine:1.29.1 (compile) "
                + "/ com.google.appengine:appengine-api-1.0-sdk:1.9.71 (provided)");
  }

  @Test
  public void testWriteLinkageErrorsAsExclusionFile()
      throws IOException, RepositoryException, TransformerException, XMLStreamException,
          LinkageCheckResultException {
    
    Path exclusionFile = Files.createTempFile("exclusion-file", ".xml");
    exclusionFile.toFile().deleteOnExit();

    // When --output-exclusion-file is specified, the tool should not throw an exception
    // upon finding linkage errors.
    LinkageCheckerMain.main(
        new String[] {
          "-a",
          "com.google.cloud:google-cloud-firestore:0.65.0-beta",
          "-o",
          exclusionFile.toString()
        });

    String output = readCapturedStdout();
    assertEquals(
        "Wrote the linkage errors as exclusion file: " + exclusionFile + System.lineSeparator(),
        output);
  }

  @Test
  public void testInvalidArgument()
      throws IOException, RepositoryException, TransformerException, XMLStreamException,
          LinkageCheckResultException {
    // This is a garbled argument Gradle Kotlin DSL passed to LinkageCheckerMain in the issue below
    // https://issues.apache.org/jira/browse/BEAM-11827
    LinkageCheckerMain.main(new String[] {"[Ljava.lang.String;@1234"});

    String output = readCapturedStdout();

    // It should show help.
    Truth.assertThat(output)
        .contains("usage: java com.google.cloud.tools.opensource.classpath.LinkageChecker");
  }

  @Test
  public void testEmptyArgument()
      throws IOException, RepositoryException, TransformerException, XMLStreamException,
          LinkageCheckResultException {
    LinkageCheckerMain.main(new String[] {});

    String output = readCapturedStdout();

    // It should show help.
    Truth.assertThat(output)
        .contains("usage: java com.google.cloud.tools.opensource.classpath.LinkageChecker");
  }
}
