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

package com.google.cloud.tools.dependencies.linkagemonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.junit.Before;
import org.junit.Test;

public class LinkageMonitorTest {
  private Bom bom;

  @Before
  public void setup() throws ArtifactDescriptorException {
    bom = RepositoryUtility.readBom("com.google.cloud:libraries-bom:1.2.0");
  }

  @Test
  public void testFindSnapshotVersion() throws VersionRangeResolutionException {
    // This version of Guava should be found locally because this module uses it.
    Bom snapshotBom =
        LinkageMonitor.copyWithSnapshot(
            RepositoryUtility.newRepositorySystem(),
            new Bom(
                "com.google.cloud.tools:test-bom:0.0.1",
                ImmutableList.of(new DefaultArtifact("com.google.guava:guava:27.1-android"))));
    assertNotNull(snapshotBom);
  }

  @Test
  public void testBomSnapshot()
      throws VersionRangeResolutionException, InvalidVersionSpecificationException {
    VersionRangeResult dummyVersionRangeResult = new VersionRangeResult(new VersionRangeRequest());
    GenericVersionScheme versionScheme = new GenericVersionScheme();
    dummyVersionRangeResult.setVersions(
        ImmutableList.of(
            versionScheme.parseVersion("1.2.3"),
            versionScheme.parseVersion("2.1.1"),
            versionScheme.parseVersion("2.1.2-SNAPSHOT")));

    RepositorySystem mockSystem = mock(RepositorySystem.class);
    when(mockSystem.resolveVersionRange(
            any(RepositorySystemSession.class), any(VersionRangeRequest.class)))
        .thenReturn(dummyVersionRangeResult);

    Bom snapshotBom = LinkageMonitor.copyWithSnapshot(mockSystem, bom);
    assertEquals("2.1.2-SNAPSHOT", snapshotBom.getManagedDependencies().get(0).getVersion());
  }
}
