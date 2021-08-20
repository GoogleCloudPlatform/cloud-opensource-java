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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.aether.artifact.Artifact;

/** Annotates {@link LinkageProblem}s with {@link LinkageProblemCause}s. */
public final class LinkageProblemCauseAnnotator {
  private static final Logger logger =
      Logger.getLogger(LinkageProblemCauseAnnotator.class.getName());

  private LinkageProblemCauseAnnotator() {}

  /**
   * Annotates the cause field of {@link LinkageProblem}s with the {@link LinkageProblemCause}.
   *
   * @param classPathBuilder class path builder to resolve dependency graphs
   * @param rootResult the class path used for generating the linkage problems
   * @param linkageProblems linkage problems to annotate
   */
  public static void annotate(
      ClassPathBuilder classPathBuilder,
      ClassPathResult rootResult,
      Iterable<LinkageProblem> linkageProblems) {
    checkNotNull(classPathBuilder);
    checkNotNull(rootResult);
    checkNotNull(linkageProblems);

    Map<Artifact, ClassPathResult> artifactResolutionCache = new HashMap<>();
    for (LinkageProblem linkageProblem : linkageProblems) {
      // Annotating linkage errors is a nice-to-have feature for Linkage Checker plugins. Let's not
      // fail the entire process if there are problems, such as classPathBuilder unable to resolve
      // one artifact or to return correct dependency tree.
      try {
        annotateProblem(classPathBuilder, rootResult, artifactResolutionCache, linkageProblem);
      } catch (Exception ex) {
        logger.warning("Failed to annotate: " + linkageProblem);
        linkageProblem.setCause(UnknownCause.getInstance());
      }
    }
  }

  private static void annotateProblem(
      ClassPathBuilder classPathBuilder,
      ClassPathResult rootResult,
      Map<Artifact, ClassPathResult> artifactResolutionCache,
      LinkageProblem linkageProblem)
      throws IOException {
    ClassFile sourceClass = linkageProblem.getSourceClass();
    ClassPathEntry sourceEntry = sourceClass.getClassPathEntry();

    Artifact sourceArtifact = sourceEntry.getArtifact();

    ClassPathResult subtreeResult = artifactResolutionCache.get(sourceArtifact);
    if (subtreeResult == null) {
      // Resolves the dependency graph with the source artifact at the root.
      subtreeResult = classPathBuilder.resolveWithMaven(sourceArtifact);
      artifactResolutionCache.put(sourceArtifact, subtreeResult);
    }

    Symbol symbol = linkageProblem.getSymbol();
    ClassPathEntry entryInSubtree = subtreeResult.findEntryBySymbol(symbol);
    if (entryInSubtree == null) {
      linkageProblem.setCause(UnknownCause.getInstance());
      return;
    }
    Artifact artifactInSubtree = entryInSubtree.getArtifact();
    ImmutableList<DependencyPath> dependencyPathsToSource =
        rootResult.getDependencyPaths(sourceEntry);
    if (dependencyPathsToSource.isEmpty()) {
      // When an artifact is excluded, it's possible to have no dependency path to sourceEntry.
      linkageProblem.setCause(UnknownCause.getInstance());
      return;
    }
    DependencyPath pathToSourceEntry = dependencyPathsToSource.get(0);
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
        ImmutableList<DependencyPath> pathToSelectedArtifact =
            rootResult.getDependencyPaths(selectedEntry);
        if (pathToSelectedArtifact.isEmpty()) {
          linkageProblem.setCause(UnknownCause.getInstance());
          return;
        }
        linkageProblem.setCause(
            new DependencyConflict(
                linkageProblem, pathToSelectedArtifact.get(0), pathToUnselectedEntry));
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
        linkageProblem.setCause(new ExcludedDependency(pathToUnselectedEntry, excludingArtifact));
      } else {
        linkageProblem.setCause(new MissingDependency(pathToUnselectedEntry));
      }
    }
  }

}
