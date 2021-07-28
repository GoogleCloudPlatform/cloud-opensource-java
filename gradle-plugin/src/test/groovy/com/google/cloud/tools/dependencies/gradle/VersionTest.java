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

package com.google.cloud.tools.dependencies.gradle;

import com.google.common.truth.Truth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.gradle.util.VersionNumber;
import org.junit.Test;

public class VersionTest {

  @Test
  public void testVersions() {
    VersionNumber v1_0_0 = VersionNumber.parse("1.2.0");
    VersionNumber v2_0_0 = VersionNumber.parse("2.0.0");
    VersionNumber v2_0_0_M5 = VersionNumber.parse("2.0.0-M5");
    VersionNumber v2_0_0_M11 = VersionNumber.parse("2.0.0-M11");

    List<VersionNumber> versions = Arrays.asList(v1_0_0, v2_0_0, v2_0_0_M5, v2_0_0_M11);
    Collections.sort(versions); // ascending order

    Truth.assertThat(versions).containsExactly(
        v1_0_0, v2_0_0_M5, v2_0_0_M11, v2_0_0
    ).inOrder();
  }
}
