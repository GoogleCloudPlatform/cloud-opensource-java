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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

  // A set to avoid printing the same circular dependency multiple times
  private Set<ResolvedComponentResult> checkedCircularDependency = new HashSet<>();

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

  static String formatComponentResult(ResolvedComponentResult componentResult) {
    ModuleVersionIdentifier identifier = componentResult.getModuleVersion();
    return identifier.toString();
  }

  /**
   * Returns mapping from Maven coordinates to their dependency paths appearing in the dependency
   * graph. The output do not have duplicate dependency paths.
   *
   * @param rootProject The root project in the dependency graph
   * @param targetCoordinatesSet The Maven coordinates to check their dependency paths
   */
  private ListMultimap<String, String> groupCoordinatesToDependencyPaths(
      ResolvedComponentResult rootProject, Set<String> targetCoordinatesSet) {

    ListMultimap<String, String> coordinatesToDependencyPaths =
        MultimapBuilder.hashKeys().arrayListValues().build();

    for (String targetCoordinates : targetCoordinatesSet) {
      // Queue of dependency nodes. Each node knows its parent.
      ArrayDeque<DependencyNode> queue = new ArrayDeque<>();
      DependencyNode firstItem =
          new DependencyNode(rootProject, null);
      queue.add(firstItem);

      // A set to omit duplicate dependency paths in the output. When a node is found to be in this
      // set while traversing the graph, we do not need to check the children, because we know that
      // the dependency paths from that node to the targetCoordinates are already added to
      // coordinatesToDependencyPaths.
      Set<ResolvedComponentResult> nodesDependOnTarget = new HashSet<>();

      while (!queue.isEmpty()) {
        DependencyNode node = queue.poll();
        ResolvedComponentResult item = node.componentResult;

        ModuleVersionIdentifier identifier = item.getModuleVersion();
        String coordinates =
            String.format(
                "%s:%s:%s", identifier.getGroup(), identifier.getName(), identifier.getVersion());
        if (targetCoordinates.equals(coordinates)) {
          String dependencyPath = node.pathFromRoot();
          coordinatesToDependencyPaths.put(targetCoordinates, dependencyPath);

          if (node.parent != null) {
            nodesDependOnTarget.addAll(node.parent.rootToNode());
          }
        }

        if (nodesDependOnTarget.contains(item)) {
          // Omitting duplicate dependency paths by checking nodesDependOnTarget.
          String dependencyPath = node.pathFromRoot() + " (omitted for duplicate)";
          coordinatesToDependencyPaths.put(targetCoordinates, dependencyPath);

          nodesDependOnTarget.addAll(node.rootToNode());
          continue;
        }

        queue.addAll(getDependencies(node));
      }
    }

    return coordinatesToDependencyPaths;
  }

  private List<DependencyNode> getDependencies(DependencyNode node) {
    ResolvedComponentResult item = node.componentResult;

    List<DependencyNode> childNodes = new ArrayList<>();
    for (DependencyResult dependencyResult : item.getDependencies()) {
      if (dependencyResult instanceof ResolvedDependencyResult) {
        ResolvedDependencyResult resolvedDependencyResult =
            (ResolvedDependencyResult) dependencyResult;
        ResolvedComponentResult child = resolvedDependencyResult.getSelected();

        if (node.isDescendantOf(child)) {
          // The child appears in the descendants of the node. It's a circular dependency.
          if (checkedCircularDependency.add(child)) {
            // No need to print the circular dependency information multiple times.
            getLogger()
                .error(
                    "Circular dependency for: "
                        + resolvedDependencyResult
                        + "\n The stack is: "
                        + node.pathFromRoot());
          }
        } else {
          childNodes.add(new DependencyNode(child, node));
        }
      } else if (dependencyResult instanceof UnresolvedDependencyResult) {
        UnresolvedDependencyResult unresolvedResult =
            (UnresolvedDependencyResult) dependencyResult;
        getLogger()
            .error(
                "Could not resolve dependency: "
                    + unresolvedResult.getAttempted().getDisplayName());
      } else {
        getLogger().error("Unexpected dependency result type: " + dependencyResult);
      }
    }
    return childNodes;
  }

  private String dependencyPathToArtifacts(
      ResolvedComponentResult componentResult, Set<ClassPathEntry> classPathEntries) {

    ImmutableSet<String> targetCoordinates =
        classPathEntries.stream()
            .map(ClassPathEntry::getArtifact)
            .map(Artifacts::toCoordinates)
            .collect(toImmutableSet());

    ListMultimap<String, String> dependencyPaths =
        groupCoordinatesToDependencyPaths(componentResult, targetCoordinates);

    StringBuilder output = new StringBuilder();
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

      // If there are multiple artifacts (with different classifiers) in this node, then the path is
      // the same, because these artifacts share the same dependencies with the same pom.xml.
      DependencyPath path = null;

      Set<ResolvedArtifact> moduleArtifacts = node.getModuleArtifacts();
      if (moduleArtifacts.isEmpty()) {
        // Unlike Maven's dependency tree, Gradle's dependency tree may include nodes that do not
        // have associated artifacts. BOMs, such as com.fasterxml.jackson:jackson-bom:2.12.3, fall
        // in this category. For the detailed observation, see the issue below:
        // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/2174#issuecomment-897174898
        getLogger()
            .warn(
                "The dependency node " + node.getName() + " does not have any artifact. Skipping.");
        continue;
      }

      // For artifacts with classifiers, there can be multiple resolved artifacts for one node
      for (ResolvedArtifact artifact : moduleArtifacts) {
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
