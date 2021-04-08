/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.opensource.classpath;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;

/** Retain only the highest version of a groupId:artifactId encountered. */
public class GradleDependencyMediation implements DependencyMediation {

  final ImmutableMap<String, String> enforcedPlatform;

  GradleDependencyMediation() {
    enforcedPlatform = ImmutableMap.of();
  }

  private GradleDependencyMediation(Bom enforcedPlatform) {
    this.enforcedPlatform =
        enforcedPlatform.getManagedDependencies().stream()
            .collect(ImmutableMap.toImmutableMap(Artifacts::makeKey, Artifact::getVersion));
  }

  /** Uses {@code enforcedPlatform} BOM when choosing versions. */
  public static GradleDependencyMediation withEnforcedPlatform(Bom enforcedPlatform) {
    return new GradleDependencyMediation(enforcedPlatform);
  }

  @Override
  public AnnotatedClassPath mediate(DependencyGraph dependencyGraph)
      throws InvalidVersionSpecificationException {

    AnnotatedClassPath annotatedClassPath = new AnnotatedClassPath();

    List<DependencyPath> dependencyPaths = dependencyGraph.list();

    HashMultimap<String, Version> coordinatesToVersions = HashMultimap.create();
    GenericVersionScheme versionScheme = new GenericVersionScheme();
    for (DependencyPath dependencyPath : dependencyPaths) {
      // DependencyPaths have items in level-order; nearest items come first.
      Artifact artifact = dependencyPath.getLeaf();
      String versionlessCoordinates = Artifacts.makeKey(artifact);
      Version version = versionScheme.parseVersion(artifact.getVersion());
      coordinatesToVersions.put(versionlessCoordinates, version);
    }

    List<String> selectedCoordinates = new ArrayList<>();
    for (String versionlessCoordinates : coordinatesToVersions.keySet()) {
      ImmutableList<Version> versions =
          coordinatesToVersions.get(versionlessCoordinates).stream()
              .sorted()
              .collect(toImmutableList());
      Version highestVersion = versions.get(versions.size() - 1);
      String selectedVersion =
          enforcedPlatform.containsKey(versionlessCoordinates)
              ? enforcedPlatform.get(versionlessCoordinates)
              : highestVersion.toString();
      selectedCoordinates.add(versionlessCoordinates + ":" + selectedVersion);
    }

    for (DependencyPath dependencyPath : dependencyPaths) {
      // DependencyPaths have items in level-order; nearest items come first.
      Artifact artifact = dependencyPath.getLeaf();
      if (selectedCoordinates.contains(Artifacts.toCoordinates(artifact))) {
        annotatedClassPath.put(new ClassPathEntry(artifact), dependencyPath);
      }
    }

    return annotatedClassPath;
  }
}
