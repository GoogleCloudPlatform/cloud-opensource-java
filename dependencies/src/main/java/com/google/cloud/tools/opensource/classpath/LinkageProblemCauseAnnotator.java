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
import java.util.HashMap;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;

/** Annotates {@link LinkageProblem}s with {@link LinkageProblemCause}s. */
public final class LinkageProblemCauseAnnotator {

  private LinkageProblemCauseAnnotator() {}

  /**
   * Annotates the cause field of {@link LinkageProblem}s with the {@link LinkageProblemCause}.
   *
   * @param classPathBuilder class path builder to resolve dependency graphs
   * @param rootResult the class path used for generating the linkage problems
   * @param linkageProblems linkage problems to annotate
   * @throws IOException when there is a problem reading JAR files
   */
  public static void annotate(
      ClassPathBuilder classPathBuilder,
      ClassPathResult rootResult,
      Iterable<LinkageProblem> linkageProblems)
      throws IOException {

    Map<Artifact, ClassPathResult> cache = new HashMap<>();

    for (LinkageProblem linkageProblem : linkageProblems) {
      ClassFile sourceClass = linkageProblem.getSourceClass();
      ClassPathEntry sourceEntry = sourceClass.getClassPathEntry();

      Artifact sourceArtifact = sourceEntry.getArtifact();

      ClassPathResult subtreeResult = cache.get(sourceArtifact);
      if (subtreeResult == null) {
        // Resolves the dependency graph with the source artifact at the root.
        subtreeResult = classPathBuilder.resolveWithMaven(sourceArtifact);
        cache.put(sourceArtifact, subtreeResult);
      }

      Symbol symbol = linkageProblem.getSymbol();
      ClassPathEntry entryInSubtree = subtreeResult.findEntryBySymbol(symbol);
      if (entryInSubtree == null) {
        linkageProblem.setCause(UnknownCause.getInstance());
      } else {
        Artifact artifactInSubtree = entryInSubtree.getArtifact();
        DependencyPath pathToSourceEntry = rootResult.getDependencyPaths(sourceEntry).get(0);
        DependencyPath pathFromSourceEntryToUnselectedEntry =
            subtreeResult.getDependencyPaths(entryInSubtree).get(0);
        DependencyPath pathToUnselectedEntry =
            pathToSourceEntry.concat(pathFromSourceEntryToUnselectedEntry);

        ClassPathEntry selectedEntry =
            rootResult.findEntryById(
                artifactInSubtree.getGroupId(), artifactInSubtree.getArtifactId());
        if (selectedEntry != null) {
          Artifact selectedArtifact = selectedEntry.getArtifact();
          if (!selectedArtifact.getVersion().equals(artifactInSubtree.getVersion())) {
            // Different version of that artifact is selected in rootResult
            linkageProblem.setCause(
                new DependencyConflict(
                    linkageProblem,
                    rootResult.getDependencyPaths(selectedEntry).get(0),
                    pathToUnselectedEntry));
          } else {
            // A linkage error was already there when sourceArtifact was built.
            linkageProblem.setCause(UnknownCause.getInstance());
          }
        } else {
          // No artifact that matches groupId and artifactId in rootResult.

          // Checking exclusion elements in the dependency path
          Artifact excludingArtifact =
              pathToSourceEntry.findExclusion(
                  artifactInSubtree.getGroupId(), artifactInSubtree.getArtifactId());
          if (excludingArtifact != null) {
            linkageProblem.setCause(
                new ExcludedDependency(pathToUnselectedEntry, excludingArtifact));
          } else {
            linkageProblem.setCause(new MissingDependency(pathToUnselectedEntry));
          }
        }
      }
    }
  }
}
