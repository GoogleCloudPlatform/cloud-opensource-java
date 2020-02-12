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

package com.google.cloud.tools.opensource.dependencies;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import java.util.List;
import java.util.Objects;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

/** Problem in a Maven artifact in a dependency tree. */
public abstract class ArtifactProblem {

  protected final Artifact artifact;

  protected final ImmutableList<DependencyNode> dependencyPath;

  // todo since the artifact is the last element in the list, there's really no
  // reason to pass the artifact here. 
  protected ArtifactProblem(Artifact artifact, List<DependencyNode> dependencyPath) {
    this.artifact = checkNotNull(artifact);
    this.dependencyPath = ImmutableList.copyOf(dependencyPath);
  }

  /**
   * Returns the dependency path to the artifact from the root of the dependency tree. An empty list
   * if the path is unknown.
   */
  protected String getPath() {
    return Joiner.on(" > ").join(dependencyPath);
  }

  /** Returns the Maven artifact that has the problem. */
  public Artifact getArtifact() {
    return artifact;
  }

  /**
   * Returns formatted string describing {@code problems} by removing similar problems per artifact.
   */
  public static String formatProblems(Iterable<ArtifactProblem> problems) {
    ImmutableListMultimap<Artifact, ArtifactProblem> artifactToProblems =
        Multimaps.index(problems, ArtifactProblem::getArtifact);

    StringBuilder output = new StringBuilder();
    for (Artifact artifact : artifactToProblems.keySet()) {
      ImmutableList<ArtifactProblem> artifactProblems = artifactToProblems.get(artifact);
      int otherCount = artifactProblems.size() - 1;
      verify(otherCount >= 0, "artifactToProblems should have at least one value for one key");
      ArtifactProblem firstProblem = Iterables.getFirst(artifactProblems, null);
      output.append(firstProblem);
      if (otherCount == 1) {
        output.append(" and a problem on the same artifact.");
      } else if (otherCount > 1) {
        output.append(" and " + otherCount + " problems on the same artifact.");
      }
      output.append("\n");
    }
    return output.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    ArtifactProblem otherProblem = (ArtifactProblem) other;
    return Objects.equals(artifact, otherProblem.artifact)
        && sameDependencyPath(dependencyPath, otherProblem.dependencyPath);
  }

  private static boolean sameDependencyPath(
      ImmutableList<DependencyNode> listA, ImmutableList<DependencyNode> listB) {
    int size = listA.size();
    if (listB.size() != size) {
      return false;
    }

    for (int i = 0; i < size; i++) {
      DependencyNode nodeA = listA.get(i);
      DependencyNode nodeB = listB.get(i);
      Dependency dependencyA = nodeA.getDependency();
      Dependency dependencyB = nodeB.getDependency();
      if (!Objects.equals(dependencyA, dependencyB)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    ImmutableList<Dependency> dependencyList =
        dependencyPath.stream().map(DependencyNode::getDependency).collect(toImmutableList());
    return Objects.hash(artifact, dependencyList);
  }
}
