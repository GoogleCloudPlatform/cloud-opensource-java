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

import com.google.cloud.tools.opensource.dependencies.DependencyGraph;

/**
 * Algorithm to select artifacts when there are multiple versions for the same groupId and
 * artifactId in a dependency graph.
 */
public interface DependencyMediation {
  /**
   * Returns the map associating the list of class path entries with their dependency paths, after
   * performing dependency mediation. This means that the returned list of class path entries have
   * no duplicate artifacts in terms of the groupId and artifactId combination.
   *
   * @param dependencyGraph dependency graph that may have duplicate artifacts that have the same
   *     groupId and artifactId combination
   */
  AnnotatedClassPath mediate(DependencyGraph dependencyGraph);
}
