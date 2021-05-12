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
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

public class BeamProjectModifierTest {
  BuildFileModifier modifier = Modification.BEAM.getModifier();
  Bom bom;
  Path copiedProject;

  @Before
  public void setup() throws Exception {
    bom = Bom.readBom("com.google.guava:guava-bom:30.0-jre");
    copiedProject = TestHelper.createProjectCopy("testdata/example-gradle-project");
  }

  @Test
  public void testRuntimeClasspathLogicChange() throws Exception {
    modifier.modifyFiles("test", copiedProject, bom);
    Path beamModulePluginFile =
        copiedProject.resolve("subproject").resolve("BeamModulePlugin.groovy");
    String beamModulePluginContent =
        Files.asCharSource(beamModulePluginFile.toFile(), StandardCharsets.UTF_8).read();

    Truth.assertThat(beamModulePluginContent)
        .contains("![\"errorprone\", \"testRuntimeClasspath\"].contains(config.getName())");
  }

  @Test
  public void testEnforcedPlatformInsertion() throws Exception {
    modifier.modifyFiles("test", copiedProject, bom);
    Path gradleFile =
        copiedProject.resolve(
            Paths.get("sdks", "java", "io", "google-cloud-platform", "build.gradle"));
    String buildGradleContent =
        Files.asCharSource(gradleFile.toFile(), StandardCharsets.UTF_8).read();

    Truth.assertThat(buildGradleContent)
        .contains("testRuntime enforcedPlatform('com.google.guava:guava-bom:30.0-jre')");
  }
}
