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

import java.io.File;
import java.util.List;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.junit.Assert;
import org.junit.Test;

public class RepositoryUtilityTest {

  @Test
  public void testFindLocalRepository() {
    RepositorySystem system = RepositoryUtility.newRepositorySystem();
    RepositorySystemSession session = RepositoryUtility.newSession(system);
    
    File local = session.getLocalRepository().getBasedir();
    Assert.assertTrue(local.exists());
    Assert.assertTrue(local.canRead());
    Assert.assertTrue(local.canWrite());
  }
  
  @Test
  public void testReadBom() throws ArtifactDescriptorException {
    Artifact artifact = new DefaultArtifact("com.google.cloud:google-cloud-bom:0.61.0-alpha");
    List<Artifact> managedDependencies = RepositoryUtility.readBom(artifact);
    // Characterization test. As long as the artifact doesn't change (and it shouldn't)
    // the answer won't change.
    Assert.assertEquals(169, managedDependencies.size());
  }
  
}
