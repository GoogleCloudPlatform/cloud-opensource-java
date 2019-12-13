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

package com.google.cloud.tools.opensource.dependencies;

import static com.google.cloud.tools.opensource.dependencies.DependencyTableArguments.readCommandLine;

import com.google.common.truth.Truth;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

public class DependencyTableArgumentsTest {

  @Test
  public void testParsingBOMs() throws ParseException {
    DependencyTableArguments arguments = readCommandLine("-b",
        "com.google.cloud:libraries-bom:2.0.0,com.google.cloud:libraries-bom:3.0.0");

    Truth.assertThat(arguments.getBomCoordinates())
        .containsExactly("com.google.cloud:libraries-bom:2.0.0",
            "com.google.cloud:libraries-bom:3.0.0");
  }

  @Test
  public void testParsingArtifacts() throws ParseException {
    DependencyTableArguments arguments = readCommandLine("-a",
        "com.foo:bar:1.0.0,com.foo:bar:2.0.0");

    Truth.assertThat(arguments.getArtifactCoordinates())
        .containsExactly("com.foo:bar:1.0.0",
            "com.foo:bar:2.0.0");
  }


  @Test
  public void testParsingBomsAndArtifacts() throws ParseException {
    DependencyTableArguments arguments = readCommandLine("-a",
        "com.foo:bar:1.0.0,com.foo:bar:2.0.0", "-b",
        "com.google.cloud:libraries-bom:2.0.0,com.google.cloud:libraries-bom:3.0.0");

    Truth.assertThat(arguments.getArtifactCoordinates())
        .containsExactly("com.foo:bar:1.0.0",
            "com.foo:bar:2.0.0");
    Truth.assertThat(arguments.getArtifactCoordinates())
        .containsExactly("com.foo:bar:1.0.0",
            "com.foo:bar:2.0.0");
  }

}
