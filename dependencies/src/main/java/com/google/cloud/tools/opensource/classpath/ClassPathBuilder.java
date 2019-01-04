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

import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;

/**
 * Utility to build a class path (a list of jar files) to be used as input for {@link
 * StaticLinkageChecker}.
 */
public class ClassPathBuilder {

  /**
   * Finds jar file paths for Maven artifacts and their dependencies.
   *
   * @param artifacts Maven artifacts to check
   * @return list of absolute paths to jar files
   * @throws RepositoryException when there is a problem in retrieving jar files
   */
  public static ImmutableList<Path> artifactsToClasspath(List<Artifact> artifacts)
      throws RepositoryException {

    LinkedListMultimap<Path, DependencyPath> multimap = artifactsToPaths(artifacts);
    return ImmutableList.copyOf(multimap.keySet());
  }


  // Multimap is a pain, maybe just use LinkedHashMap<Path, List<DependencyPath>>
  /**
   * Finds jar file paths for Maven artifacts and their dependencies.
   *
   * @param artifacts Maven artifacts to check
   * @return map absolute paths of jar files to Maven dependency paths
   * @throws RepositoryException when there is a problem in retrieving jar files
   */
  public static LinkedListMultimap<Path, DependencyPath> artifactsToPaths(List<Artifact> artifacts)
      throws RepositoryException {

    LinkedListMultimap<Path, DependencyPath> multimap = LinkedListMultimap.create();
    if (artifacts.isEmpty()) {
      return multimap;
    }
    // dependencyGraph holds multiple versions for one artifact key (groupId:artifactId)
    DependencyGraph dependencyGraph =
        DependencyGraphBuilder.getStaticLinkageCheckDependencies(artifacts);
    List<DependencyPath> dependencyPaths = dependencyGraph.list();

    for (DependencyPath dependencyPath : dependencyPaths) {
      Artifact artifact = dependencyPath.getLeaf();
      Path jarAbsolutePath = artifact.getFile().toPath().toAbsolutePath();
      if (!jarAbsolutePath.toString().endsWith(".jar")) {
        continue;
      }
      multimap.put(jarAbsolutePath, dependencyPath);
    }
    return multimap;
  }
}
