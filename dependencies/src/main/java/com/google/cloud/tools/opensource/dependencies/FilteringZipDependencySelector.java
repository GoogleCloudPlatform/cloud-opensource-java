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

import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

/** Excludes artifacts with {@code zip} type. */
public final class FilteringZipDependencySelector implements DependencySelector {
  // To exclude log4j-api-java9:zip:2.11.1, which is not published.
  // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/339

  @Override
  public boolean selectDependency(Dependency dependency) {
    Artifact artifact = dependency.getArtifact();
    Map<String, String> properties = artifact.getProperties();
    // Because LinkageChecker only checks jar file, zip files are not needed
    return !"zip".equals(properties.get("type"));
  }

  @Override
  public DependencySelector deriveChildSelector(
      DependencyCollectionContext dependencyCollectionContext) {
    return this;
  }
}
