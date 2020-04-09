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

  private final ByteArrayOutputStream capturedOutputStream = new ByteArrayOutputStream();
  private final PrintStream originalStandardOut = System.out;

  @Before
  public void setup() {
    System.setOut(new PrintStream(capturedOutputStream));
  }

  @After
  public void cleanup() {
    System.setOut(originalStandardOut);
  }

  @Test
  public void testJarFiles() throws IOException, URISyntaxException, RepositoryException {

    Path googleCloudCore = absolutePathOfResource("testdata/google-cloud-core-1.48.0.jar");
    Path googleCloudFirestore =
        absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar");
    Path guava = absolutePathOfResource("testdata/guava-23.5-jre.jar");

    String jarArgument = googleCloudCore + "," + googleCloudFirestore + "," + guava;

    // This should not raise IOException
    LinkageCheckerMain.main(new String[] {"-j", jarArgument});

    String output = new String(capturedOutputStream.toByteArray());

    // Gax is not in the JAR list
    Truth.assertThat(output)
        .contains(
            "Class com.google.api.gax.retrying.ResultRetryAlgorithm is not found;\n"
                + "  referenced by 1 class file\n"
                + "    com.google.cloud.ExceptionHandler ("
                + googleCloudCore
                + ")");
  }
}
