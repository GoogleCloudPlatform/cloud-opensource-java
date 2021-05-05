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

package com.google.cloud.tools.opensource.dashboard;

import org.junit.Assert;
import org.junit.Test;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class MavenCentralTest {

  @Test
  public void testBuildUrl() {
    Artifact artifact = new DefaultArtifact("com.google.guava:guava:30.1.1-jre");
    Assert.assertEquals("https://repo1.maven.org/maven2/com/google/guava/guava/30.1.1-jre/",
        MavenCentral.buildUrl(artifact));
  }

}
