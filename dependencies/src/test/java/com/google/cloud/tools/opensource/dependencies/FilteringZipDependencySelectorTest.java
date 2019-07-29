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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

public class FilteringZipDependencySelectorTest {

  private FilteringZipDependencySelector selector = new FilteringZipDependencySelector();

  @Test
  public void testDeriveChildSelector() {
    DependencySelector derivedSelector = selector.deriveChildSelector(null);
    assertSame(selector, derivedSelector);
  }

  @Test
  public void testFilteringZip() {
    Artifact artifact =
        new DefaultArtifact(
            "org.apache.logging.log4j:log4j-api-java9:zip:2.11.1", ImmutableMap.of("type", "zip"));
    Dependency dependency = new Dependency(artifact, "compile");
    assertFalse(selector.selectDependency(dependency));
  }

  @Test
  public void testFilteringNonZip() {
    Artifact artifact = new DefaultArtifact("com.google.guava", "guava", "jar", "28.0-android");
    Dependency dependency = new Dependency(artifact, "compile");
    assertTrue(selector.selectDependency(dependency));
  }
}
