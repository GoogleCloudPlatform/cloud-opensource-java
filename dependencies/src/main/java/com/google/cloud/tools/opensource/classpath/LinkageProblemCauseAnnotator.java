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

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import java.io.IOException;
import org.eclipse.aether.artifact.Artifact;

/** Annotates {@link LinkageProblem}s with {@link LinkageProblemCause}s. */
public class LinkageProblemCauseAnnotator {

  static void annotate(ClassPathResult rootResult, Iterable<LinkageProblem> linkageProblems)
      throws IOException {

    for (LinkageProblem linkageProblem : linkageProblems) {
      ClassFile sourceClass = linkageProblem.getSourceClass();
      ClassPathEntry sourceEntry = sourceClass.getClassPathEntry();

      Artifact sourceArtifact = sourceEntry.getArtifact();
      if (sourceArtifact == null) {
        // This
        continue;
      }

      ClassPathBuilder classPathBuilder = new ClassPathBuilder();
      ClassPathResult subtreeResult = classPathBuilder.resolveWithMaven(sourceArtifact);

      // subtreeResult should contain hadoop-client but it does not
      ClassPathEntry entryInSubtree =
          findClassPathEntryForSymbol(subtreeResult, linkageProblem.getSymbol());
      if (entryInSubtree == null) {
        linkageProblem.setCause(new UnknownCause());
      } else {
        Artifact artifactInSubtree = entryInSubtree.getArtifact();
        DependencyPath pathToSourceEntry = rootResult.getDependencyPaths(sourceEntry).get(0);
        DependencyPath pathFromSourceEntryToUnselectedEntry =
            subtreeResult.getDependencyPaths(entryInSubtree).get(0);
        DependencyPath pathToUnselectedEntry =
            pathToSourceEntry.concat(pathFromSourceEntryToUnselectedEntry);

        ClassPathEntry selectedEntry =
            findEntryByArtifactId(
                rootResult, artifactInSubtree.getGroupId(), artifactInSubtree.getArtifactId());
        if (selectedEntry != null) {
          Artifact selectedArtifact = selectedEntry.getArtifact();
          if (!selectedArtifact.getVersion().equals(artifactInSubtree.getVersion())) {
            // Different version of that artifact is selected in rootResult
            linkageProblem.setCause(
                new DependencyConflict(
                    rootResult.getDependencyPaths(selectedEntry).get(0), pathToUnselectedEntry));
          } else {
            linkageProblem.setCause(new UnknownCause());
          }
        } else {
          // No artifact that match groupId and artifactId
          linkageProblem.setCause(new MissingDependency(pathToUnselectedEntry));
        }
      }
    }
  }

  /**
   * Returns the class path entry in {@code classPathResult} that holds the artifact that matches
   * {@code groupId} and {@code artifactId}. {@code Null} if matching artifact is not found.
   */
  private static ClassPathEntry findEntryByArtifactId(
      ClassPathResult classPathResult, String groupId, String artifactId) {
    for (ClassPathEntry entry : classPathResult.getClassPath()) {
      Artifact artifact = entry.getArtifact();
      if (artifact.getGroupId().equals(groupId) && artifact.getArtifactId().equals(artifactId)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * Returns the {@link ClassPathEntry} in {@code classPathResult} that contains the class of {@code
   * symbol}. Null if no matching entry is found.
   */
  private static ClassPathEntry findClassPathEntryForSymbol(
      ClassPathResult classPathResult, Symbol symbol) throws IOException {
    String className = symbol.getClassBinaryName();
    for (ClassPathEntry entry : classPathResult.getClassPath()) {
      if (entry.getFileNames().contains(className)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * Returns the dependency path of the first {@link ClassPathEntry} in {@code classPathResult} that
   * contains the class of {@code symbol}. Null if no such entry is found.
   */
  private static DependencyPath findDependencyPathForSymbol(
      ClassPathResult classPathResult, Symbol symbol) throws IOException {
    ClassPathEntry entry = findClassPathEntryForSymbol(classPathResult, symbol);
    if (entry != null) {
      return classPathResult.getDependencyPaths(entry).get(0);
    }
    return null;
  }
}
