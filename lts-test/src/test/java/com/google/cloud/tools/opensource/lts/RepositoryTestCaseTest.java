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

import com.google.common.testing.EqualsTester;
import java.net.URL;
import org.junit.Test;

public class RepositoryTestCaseTest {

  @Test
  public void testEquals() throws Exception {
    RepositoryTestCase caseBar =
        new RepositoryTestCase(
            "bar", new URL("http://foo.com/bar.git"), "v1.2.3", Modification.MAVEN, "mvn test");
    RepositoryTestCase caseBaz =
        new RepositoryTestCase(
            "baz", new URL("http://foo.com/baz.git"), "v1.2.3", Modification.MAVEN, "mvn test");
    new EqualsTester()
        .addEqualityGroup(
            caseBar,
            new RepositoryTestCase(
                "bar", new URL("http://foo.com/bar.git"), "v1.2.3", Modification.MAVEN, "mvn test"))
        .addEqualityGroup(caseBaz, caseBaz)
        .addEqualityGroup(
            new RepositoryTestCase(
                "bar",
                new URL("http://foo.com/bar.git"),
                "v1.2.3",
                Modification.BEAM, // Different
                "mvn test"))
        .addEqualityGroup(
            new RepositoryTestCase(
                "bar",
                new URL("http://foo.com/bar.git"),
                "v1.2.3",
                Modification.MAVEN,
                "./gradlew test" // Different
                ))
        .testEquals();
  }
}
