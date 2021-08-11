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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.cloud.tools.opensource.classpath.AnnotatedClassPath;
import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.LinkageProblem;
import com.google.cloud.tools.opensource.classpath.LinkageProblemCauseAnnotator;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.PathToNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
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

  /** Returns true iff {@code configuration}'s artifacts contain linkage errors. */
  private boolean findLinkageErrors(Configuration configuration) throws IOException {

    ClassPathResult classPathResult =
        createClassPathResult(configuration.getResolvedConfiguration());
    ImmutableList.Builder<ClassPathEntry> classPathEntriesBuilder = ImmutableList.builder();

    for (ResolvedArtifact resolvedArtifact :
        configuration.getResolvedConfiguration().getResolvedArtifacts()) {
      ModuleVersionIdentifier moduleVersionId = resolvedArtifact.getModuleVersion().getId();
      DefaultArtifact artifact =
          new DefaultArtifact(
              moduleVersionId.getGroup(),
              moduleVersionId.getName(),
              resolvedArtifact.getClassifier(),
              resolvedArtifact.getExtension(),
              moduleVersionId.getVersion(),
              null,
              resolvedArtifact.getFile());
      classPathEntriesBuilder.add(new ClassPathEntry(artifact));
    }

    ImmutableList<ClassPathEntry> classPath = classPathEntriesBuilder.build();

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

      ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

      ClassPathBuilder classPathBuilder = new ClassPathBuilder();
      LinkageProblemCauseAnnotator.annotate(classPathBuilder, classPathResult, linkageProblems);

      int errorCount = linkageProblems.size();

      // TODO(suztomo): Show the dependency paths to the problematic artifacts.
      if (errorCount > 0) {
        getLogger()
            .error(
                "Linkage Checker rule found {} error{}:\n{}",
                errorCount,
                errorCount > 1 ? "s" : "",
                LinkageProblem.formatLinkageProblems(linkageProblems, classPathResult));

        ResolutionResult result = configuration.getIncoming().getResolutionResult();
        ResolvedComponentResult root = result.getRoot();
        String dependencyPaths = dependencyPathsOfProblematicJars(root, linkageProblems);
        getLogger().error(dependencyPaths);
        getLogger()
            .info(
                "For the details of the linkage errors, see "
                    + "https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Messages");
      }
      return errorCount > 0;
    }
    // When the configuration does not have any artifacts, there's no linkage error.
    return false;
  }

  private String dependencyPathsOfProblematicJars(
      ResolvedComponentResult componentResult, Set<LinkageProblem> symbolProblems) {
    ImmutableSet.Builder<ClassPathEntry> problematicJars = ImmutableSet.builder();
    for (LinkageProblem problem : symbolProblems) {


      ClassFile targetClass = problem.getTargetClass();
      if (targetClass != null) {
        problematicJars.add(targetClass.getClassPathEntry());
      }

      ClassFile sourceClass = problem.getSourceClass();
      problematicJars.add(sourceClass.getClassPathEntry());
    }

    return "Problematic artifacts in the dependency tree:\n"
        + dependencyPathToArtifacts(componentResult, problematicJars.build());
  }

  String formatComponentResult(ResolvedComponentResult componentResult) {
    ModuleVersionIdentifier identifier = componentResult.getModuleVersion();
    return identifier.toString();
  }

  private void recordDependencyPaths(
      ImmutableListMultimap.Builder<String, String> output,
      ArrayDeque<ResolvedComponentResult> stack,
      ImmutableSet<String> targetCoordinates) {
    ResolvedComponentResult item = stack.getLast();
    ModuleVersionIdentifier identifier = item.getModuleVersion();
    String coordinates =
        String.format(
            "%s:%s:%s", identifier.getGroup(), identifier.getName(), identifier.getVersion());
    if (targetCoordinates.contains(coordinates)) {
      String dependencyPath =
          stack.stream().map(this::formatComponentResult).collect(Collectors.joining(" / "));
      output.put(coordinates, dependencyPath);
    }

    for (DependencyResult dependencyResult : item.getDependencies()) {
      if (dependencyResult instanceof ResolvedDependencyResult) {
        ResolvedDependencyResult resolvedDependencyResult =
            (ResolvedDependencyResult) dependencyResult;
        ResolvedComponentResult child = resolvedDependencyResult.getSelected();
        stack.add(child);
        recordDependencyPaths(output, stack, targetCoordinates);
      } else if (dependencyResult instanceof UnresolvedDependencyResult) {
        UnresolvedDependencyResult unresolvedResult = (UnresolvedDependencyResult) dependencyResult;
        getLogger()
            .error(
                "Could not resolve dependency: "
                    + unresolvedResult.getAttempted().getDisplayName());
      } else {
        throw new IllegalStateException("Unexpected dependency result type: " + dependencyResult);
      }
    }

    stack.removeLast();
  }

  private String dependencyPathToArtifacts(
      ResolvedComponentResult componentResult, Set<ClassPathEntry> classPathEntries) {

    ImmutableSet<String> targetCoordinates =
        classPathEntries.stream()
            .map(ClassPathEntry::getArtifact)
            .map(Artifacts::toCoordinates)
            .collect(toImmutableSet());

    StringBuilder output = new StringBuilder();

    ArrayDeque<ResolvedComponentResult> stack = new ArrayDeque<>();
    stack.add(componentResult);

    ImmutableListMultimap.Builder<String, String> coordinatesToDependencyPaths =
        ImmutableListMultimap.builder();

    recordDependencyPaths(coordinatesToDependencyPaths, stack, targetCoordinates);

    ImmutableListMultimap<String, String> dependencyPaths = coordinatesToDependencyPaths.build();
    for (String coordinates : dependencyPaths.keySet()) {
      output.append(coordinates + " is at:\n");
      for (String dependencyPath : dependencyPaths.get(coordinates)) {
        output.append("  " + dependencyPath + "\n");
      }
    }

    return output.toString();
  }

  private static Artifact artifactFrom(
      ResolvedDependency resolvedDependency, ResolvedArtifact resolvedArtifact) {
    ModuleVersionIdentifier moduleVersionId = resolvedDependency.getModule().getId();
    DefaultArtifact artifact =
        new DefaultArtifact(
            moduleVersionId.getGroup(),
            moduleVersionId.getName(),
            resolvedArtifact.getClassifier(),
            resolvedArtifact.getExtension(),
            moduleVersionId.getVersion(),
            null,
            resolvedArtifact.getFile());
    return artifact;
  }

  private static Dependency dependencyFrom(
      ResolvedDependency resolvedDependency, ResolvedArtifact resolvedArtifact) {
    Artifact artifact = artifactFrom(resolvedDependency, resolvedArtifact);
    return new Dependency(artifact, "compile");
  }

  private DependencyGraph createDependencyGraph(ResolvedConfiguration configuration) {
    // Why this method is not part of the DependencyGraph? Because the dependencies module
    // which the DependencyGraph belongs to is a Maven project, and Gradle does not provide good
    // Maven artifacts to develop code with Gradle-related classes.
    // For the details, see https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/1556

    // The root Gradle project is not available in `configuration`.
    DependencyGraph graph = new DependencyGraph(null);

    ArrayDeque<PathToNode<ResolvedDependency>> queue = new ArrayDeque<>();

    DependencyPath root = new DependencyPath(null);
    for (ResolvedDependency firstLevelDependency :
        configuration.getFirstLevelModuleDependencies()) {
      queue.add(new PathToNode<>(firstLevelDependency, root));
    }

    Set<ResolvedDependency> visited = new HashSet<>();
    while (!queue.isEmpty()) {
      PathToNode<ResolvedDependency> item = queue.poll();
      ResolvedDependency node = item.getNode();

      DependencyPath parentPath = item.getParentPath();

      DependencyPath path = null;

      if (node.getModuleArtifacts().isEmpty()) {
        getLogger()
            .warn(
                "The dependency node "
                    + node.getName()
                    + " should have at least one resolved artifacts");
        continue;
      }
      // For artifacts with classifiers, there can be multiple resolved artifacts for one node
      for (ResolvedArtifact artifact : node.getModuleArtifacts()) {
        // parentPath is null for the first item
        path =
            parentPath == null
                ? new DependencyPath(artifactFrom(node, artifact))
                : parentPath.append(dependencyFrom(node, artifact));
        graph.addPath(path);
      }

      for (ResolvedDependency child : node.getChildren()) {
        if (visited.add(child)) {
          queue.add(new PathToNode<>(child, path));
        }
      }
    }

    return graph;
  }

  private ClassPathResult createClassPathResult(ResolvedConfiguration configuration) {
    DependencyGraph dependencyGraph = createDependencyGraph(configuration);
    AnnotatedClassPath annotatedClassPath = new AnnotatedClassPath();

    for (DependencyPath path : dependencyGraph.list()) {
      Artifact artifact = path.getLeaf();
      annotatedClassPath.put(new ClassPathEntry(artifact), path);
    }
    return new ClassPathResult(annotatedClassPath, ImmutableList.of());
  }
}
