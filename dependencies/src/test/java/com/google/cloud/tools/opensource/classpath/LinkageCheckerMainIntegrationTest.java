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

import com.google.common.truth.Truth;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.eclipse.aether.RepositoryException;
import org.junit.After;
import org.junit.Before;
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
  public void testJarFiles() throws IOException, URISyntaxException, RepositoryException {

    Path googleCloudCore = absolutePathOfResource("testdata/google-cloud-core-1.48.0.jar");
    Path googleCloudFirestore =
        absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar");
    Path guava = absolutePathOfResource("testdata/guava-23.5-jre.jar");

    String jarArgument = googleCloudCore + "," + googleCloudFirestore + "," + guava;

    // This should not raise Exception
    LinkageCheckerMain.main(new String[] {"-j", jarArgument});

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
  public void testArtifacts() throws IOException, URISyntaxException, RepositoryException {
    LinkageCheckerMain.main(
        new String[] {"-a", "com.google.cloud:google-cloud-firestore:0.65.0-beta"});

    String output = readCapturedStdout();
    Truth.assertThat(output)
        .contains(
            "Class com.jcraft.jzlib.JZlib is not found;\n"
                + "  referenced by 4 class files\n"
                + "    io.grpc.netty.shaded.io.netty.handler.codec.spdy.SpdyHeaderBlockJZlibEncoder"
                + " (io.grpc:grpc-netty-shaded:1.13.1)");

    // Show the dependency path to the problematic artifact
    Truth.assertThat(output)
        .contains(
            "io.grpc:grpc-netty-shaded:1.13.1 is at:\n"
                + "  com.google.cloud:google-cloud-firestore:0.65.0-beta (compile) /"
                + " io.grpc:grpc-netty-shaded:1.13.1 (compile)\n"
                + "  and 1 dependency path.");
  }
}
