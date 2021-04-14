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

package com.google.cloud.tools.opensource.classpath;

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * A class path that consists of a list of artifacts, each of which is annotated with dependency
 * paths. The dependency paths tell where an artifact appears in the dependency graph. Note that the
 * same artifact (having the same groupId, artifactId, and version) may appear in a graph multiple
 * times.
 */
public class AnnotatedClassPath {

  /**
   * An ordered map from class path elements to one or more Maven dependency paths.
   *
   * <p>The keys of the returned map represent Maven artifacts in the resolved class path, including
   * transitive dependencies. The return value of {@link LinkedListMultimap#keySet()} preserves key
   * iteration order.
   *
   * <p>The values of the returned map for a key (class path entry) represent the different
   * dependency paths from {@code artifacts} to the Maven artifact.
   */
  private LinkedListMultimap<ClassPathEntry, DependencyPath> classPathEntryToDependencyPaths =
      LinkedListMultimap.create();

  /**
   * Adds {@code classPathEntry} with {@code dependencyPath}. The dependency path represents a path
   * from the root of the dependency graph to the artifact.
   */
  public void put(ClassPathEntry classPathEntry, DependencyPath dependencyPath) {
    classPathEntryToDependencyPaths.put(classPathEntry, dependencyPath);
  }

  ImmutableList<ClassPathEntry> getClassPath() {
    // LinkedListMultimap.keySet preserves the iteration order
    return ImmutableList.copyOf(classPathEntryToDependencyPaths.keySet());
  }

  ImmutableList<DependencyPath> pathsTo(ClassPathEntry classPathEntry) {
    return ImmutableList.copyOf(classPathEntryToDependencyPaths.get(classPathEntry));
  }

  /**
   * Returns an {@link AnnotatedClassPath} that has class path entry and dependency paths
   * relationship represented in {@code classPathEntryToDependencyPaths}.
   */
  @VisibleForTesting
  public static AnnotatedClassPath fromMultimap(
      ListMultimap<ClassPathEntry, DependencyPath> classPathEntryToDependencyPaths) {
    AnnotatedClassPath annotatedClassPath = new AnnotatedClassPath();
    annotatedClassPath.classPathEntryToDependencyPaths.putAll(classPathEntryToDependencyPaths);
    return annotatedClassPath;
  }
}
