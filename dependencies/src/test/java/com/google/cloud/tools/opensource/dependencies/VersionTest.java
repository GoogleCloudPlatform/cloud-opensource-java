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

package com.google.cloud.tools.opensource.dependencies;

import com.google.common.truth.Truth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.Version;
import org.junit.Test;
import sun.net.www.content.text.Generic;

public class VersionTest {
  @Test
  public void versionTest() throws Exception {
    GenericVersionScheme scheme = new GenericVersionScheme();
    Version v1_0_0 = scheme.parseVersion("1.2.0");
    Version v2_0_0 = scheme.parseVersion("2.0.0");
    Version v2_0_0_M5 = scheme.parseVersion("2.0.0-M5");
    Version v2_0_0_M11 = scheme.parseVersion("2.0.0-M11");

    List<Version> versions = Arrays.asList(v1_0_0, v2_0_0, v2_0_0_M5, v2_0_0_M11);
    Collections.sort(versions); // ascending order

    Truth.assertThat(versions).containsExactly(
        v1_0_0, v2_0_0_M5, v2_0_0_M11, v2_0_0
    ).inOrder();
  }
}
