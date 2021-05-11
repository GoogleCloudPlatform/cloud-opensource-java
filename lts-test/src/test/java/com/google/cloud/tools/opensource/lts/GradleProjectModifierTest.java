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


import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.Test;

public class GradleProjectModifierTest {
  @Test
  public void testEnforcedPlatformInsertion() throws Exception {
    BuildFileModifier modifier = Modification.GRADLE.getModifier();
    Bom bom = Bom.readBom("com.google.guava:guava-bom:30.0-jre");

    Path copiedProject = TestHelper.createProjectCopy("testdata/example-gradle-project");
    modifier.modifyFiles("test", copiedProject, bom);

    Path gradleFile = copiedProject.resolve("subproject").resolve("build.gradle");
    String buildGradleContent =
        Files.asCharSource(gradleFile.toFile(), StandardCharsets.UTF_8).read();

    Truth.assertThat(buildGradleContent)
        .contains("testRuntime enforcedPlatform('com.google.guava:guava-bom:30.0-jre')");
  }
}
