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

package com.google.cloud.tools.dependencies.gradle;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.tasks.TaskAction;

/**
 * Task to run Linkage Checker for the dependencies of the Gradle project.
 */
public class LinkageCheckTask extends DefaultTask {
  private LinkageCheckerPluginExtension extension;

  @TaskAction
  public void run() throws IOException {
    extension = getProject().getExtensions().findByType(LinkageCheckerPluginExtension.class);
    if (extension == null) {
      extension = new LinkageCheckerPluginExtension();
    }

    Project project = getProject();
    ImmutableSet.Builder<Configuration> configurationsBuilder = ImmutableSet.builder();
    if (extension.getConfigurations().isEmpty()) {
      // Should this default to runtime?
      getLogger().trace("No configuration specified, defaulting to all configurations");
      for (Configuration configuration : project.getConfigurations()) {
        if (configuration.isCanBeResolved()) {
          configurationsBuilder.add(configuration);
        }
      }
    } else {
      for (String configurationName : extension.getConfigurations()) {
        Configuration configuration = project.getConfigurations().getByName(configurationName);
        if (configuration.isCanBeResolved()) {
          configurationsBuilder.add(configuration);
        }
      }
    }

    ImmutableSet<Configuration> configurations = configurationsBuilder.build();

    boolean foundError = false;
    for (Configuration configuration : configurations) {
      if (findLinkageErrors(configuration)) {
        foundError = true;
      }
    }

    if (foundError) {
      throw new GradleException(
          "Linkage Checker found errors in configurations. See above for the details.");
    }
  }

  String dependencyPathToArtifacts(ResolvedComponentResult componentResult,
      Set<ClassPathEntry> classPathEntries) {
    ModuleVersionIdentifier identifier = componentResult.getModuleVersion();
    DependencyNode node = new DefaultDependencyNode(
        new DefaultArtifact(identifier.getGroup(),
            identifier.getName(),
            null,
            identifier.getVersion()
            )
    );
    ImmutableList.Builder<DependencyNode> childrenBuilder = ImmutableList.builder();
    for (DependencyResult dependencyResult : componentResult.getDependencies()) {
      if (dependencyResult instanceof ResolvedDependencyResult) {
        DependencyNode child = convert(((ResolvedDependencyResult) dependencyResult).getSelected());
        childrenBuilder.add(child);
      } else if (dependencyResult instanceof UnresolvedDependencyResult) {
        UnresolvedDependencyResult unresolvedResult = (UnresolvedDependencyResult) dependencyResult;
        getLogger().error("Could not resolve dependency: " + unresolvedResult.getAttempted().getDisplayName());
      } else {
        throw new IllegalStateException("Unexpected dependency result type: "+ dependencyResult);
      }
    }
    node.setChildren(childrenBuilder.build());
    return node;
  }

  private DependencyGraph createDependencyGraph(ResolvedComponentResult componentResult) {
    DependencyNode root = convert(componentResult);
    return DependencyGraph.from(root);
  }

  /** Returns true iff {@code configuration}'s artifacts contain linkage errors. */
  private boolean findLinkageErrors(Configuration configuration) throws IOException {
    ImmutableList.Builder<ClassPathEntry> classPathBuilder = ImmutableList.builder();

    ResolutionResult result = configuration.getIncoming().getResolutionResult();
    ResolvedComponentResult root = result.getRoot();
    DependencyGraph graph = createDependencyGraph(root);

    // TODO(suztomo): Should this include optional dependencies?
    //  Once we decide what to do with the optional dependencies, let's revisit this logic.
    for (ResolvedArtifact resolvedArtifact :
        configuration.getResolvedConfiguration().getResolvedArtifacts()) {
      ModuleVersionIdentifier moduleVersionId = resolvedArtifact.getModuleVersion().getId();
      DefaultArtifact artifact =
          new DefaultArtifact(
              moduleVersionId.getGroup(),
              moduleVersionId.getName(),
              null,
              null,
              moduleVersionId.getVersion(),
              null,
              resolvedArtifact.getFile());
      classPathBuilder.add(new ClassPathEntry(artifact));
    }

    ImmutableList<ClassPathEntry> classPath = classPathBuilder.build();

    if (!classPath.isEmpty()) {
      String exclusionFileName = extension.getExclusionFile();
      Path exclusionFile = exclusionFileName == null ? null : Paths.get(exclusionFileName);
      if (exclusionFile != null && !exclusionFile.isAbsolute()) {
        // Relative path from the project root
        Path projectRoot = getProject().getRootDir().toPath();
        exclusionFile = projectRoot.resolve(exclusionFile).toAbsolutePath();
      }

      // TODO(suztomo): Specify correct entry points if reportOnlyReachable is true.
      LinkageChecker linkageChecker = LinkageChecker.create(classPath, classPath, exclusionFile);

      ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
          linkageChecker.findSymbolProblems();

      int errorCount = symbolProblems.keySet().size();

      // TODO(suztomo): Show the dependency paths to the problematic artifacts.
      if (errorCount > 0) {
        getLogger().error(
            "Linkage Checker rule found {} error{}. Linkage error report:\n{}",
            errorCount,
            errorCount > 1 ? "s" : "",
            SymbolProblem.formatSymbolProblems(symbolProblems));

        ClassPathResult classPathResult = new ClassPathResult(graph.)
        String dependencyPaths =
            dependencyPathsOfProblematicJars(classPathResult, symbolProblems);

      }
      return errorCount > 0;
    }
    // When the configuration does not have any artifacts, there's no linkage error.
    return false;
  }

  private String dependencyPathsOfProblematicJars(
      ClassPathResult classPathResult, Multimap<SymbolProblem, ClassFile> symbolProblems) {
    ImmutableSet.Builder<ClassPathEntry> problematicJars = ImmutableSet.builder();
    for (SymbolProblem problem : symbolProblems.keySet()) {
      ClassFile containingClass = problem.getContainingClass();
      if (containingClass != null) {
        problematicJars.add(containingClass.getClassPathEntry());
      }

      for (ClassFile classFile : symbolProblems.get(problem)) {
        problematicJars.add(classFile.getClassPathEntry());
      }
    }

    return "Problematic artifacts in the dependency tree:\n"
        + classPathResult.formatDependencyPaths(problematicJars.build());
  }


}
