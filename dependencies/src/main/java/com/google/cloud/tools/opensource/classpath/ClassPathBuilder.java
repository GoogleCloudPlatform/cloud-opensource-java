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

package com.google.cloud.tools.opensource.classpath;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;

/**
 * Utility to build a class path (a list of jar files) to be used as input for {@link
 * StaticLinkageChecker}.
 */
public class ClassPathBuilder {

  /**
   * Finds jar file paths for Maven artifacts and their transitive dependencies.
   *
   * @param artifacts Maven artifacts to check
   * @return list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in retrieving jar files
   * @see <a
   *     href="https://docs.oracle.com/javase/8/docs/technotes/tools/unix/classpath.html#sthref15">Setting
   *     the Class Path: Specification Order</a>
   */
  public static ImmutableList<Path> artifactsToClasspath(List<Artifact> artifacts)
      throws RepositoryException {

    LinkedListMultimap<Path, DependencyPath> multimap = artifactsToPaths(artifacts);
    return ImmutableList.copyOf(multimap.keySet());
  }

  /**
   * Finds jar file paths and dependency paths for Maven artifacts and their transitive
   * dependencies. When there are multiple versions of an artifact, the closest to the root ({@code
   * artifacts}) is picked up. This 'pick closest' strategy follows Maven's dependency mediation.
   * The values of the returned map for a key (jar file) represent the different Maven dependency
   * paths from {@code artifacts} to the Maven artifact of the jar file.
   *
   * @param artifacts Maven artifacts to check
   * @return map absolute paths of jar files to one or more Maven dependency paths
   * @throws RepositoryException when there is a problem in retrieving jar files
   * @see <a
   *     href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Transitive_Dependencies">Maven:
   *     Introduction to the Dependency Mechanism</a>
   */
  public static LinkedListMultimap<Path, DependencyPath> artifactsToPaths(List<Artifact> artifacts)
      throws RepositoryException {
    // TODO(#315): Consider using LinkedHashMap<Path, List<DependencyPath>> over Multimap
    // Multimap has many variation with different behaviors.

    LinkedListMultimap<Path, DependencyPath> multimap = LinkedListMultimap.create();
    if (artifacts.isEmpty()) {
      return multimap;
    }
    // dependencyGraph holds multiple versions for one artifact key (groupId:artifactId)
    DependencyGraph dependencyGraph =
        DependencyGraphBuilder.getStaticLinkageCheckDependencyGraph(artifacts);
    List<DependencyPath> dependencyPaths = dependencyGraph.list();

    // To remove duplicates on (groupId:artifactId) for dependency mediation
    Map<String, String> keyToFirstArtifactVersion = Maps.newHashMap();

    for (DependencyPath dependencyPath : dependencyPaths) {
      Artifact artifact = dependencyPath.getLeaf();
      Path jarAbsolutePath = artifact.getFile().toPath().toAbsolutePath();
      if (!jarAbsolutePath.toString().endsWith(".jar")) {
        continue;
      }

      String artifactVersion = artifact.getVersion();
      // groupId:artifactId
      String dependencyMediationKey = Artifacts.makeKey(artifact);
      String firstArtifactVersionForKey = keyToFirstArtifactVersion.get(dependencyMediationKey);
      if (firstArtifactVersionForKey != null
          && !artifactVersion.equals(firstArtifactVersionForKey)) {
        // Not adding this artifact if different version of the artifact (<groupId>:<artifactId> as
        // key) is already in `multimap`.
        // As `dependencyPaths` elements are in level order (breadth-first), this first-wins
        // strategy follows Maven's dependency mediation.
        // TODO(#309): add Gradle's dependency mediation
        continue;
      }
      keyToFirstArtifactVersion.put(dependencyMediationKey, artifact.getVersion());

      // When finding the key (groupId:artifactId) first time, or additional dependency path to
      // the artifact of the same version is encountered, adds the dependency path to `multimap`.
      multimap.put(jarAbsolutePath, dependencyPath);
    }
    return multimap;
  }

}
