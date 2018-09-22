/*
 * Copyright 2018 Google LLC.
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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Test;

public class ArtifactsTest {

  @Test
  public void testToCoordinates() {
    Artifact artifact = new DefaultArtifact("com.google.example:example:1.3.2");
    Assert.assertEquals("com.google.example:example:1.3.2", Artifacts.toCoordinates(artifact));
  }
  
  @Test
  public void testToCoordinates_withPackaging() {
    Artifact artifact = new DefaultArtifact("com.google.example", "example", "jar", "1.3.2");
    Assert.assertEquals("com.google.example:example:1.3.2", Artifacts.toCoordinates(artifact));
  }
  
}
