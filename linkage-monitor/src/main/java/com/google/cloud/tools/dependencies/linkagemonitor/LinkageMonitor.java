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

package com.google.cloud.tools.dependencies.linkagemonitor;

import static com.google.cloud.tools.opensource.dependencies.RepositoryUtility.CENTRAL;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.project.ProjectModelResolver;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Linkage Monitor detects new linkage errors caused by locally-installed snapshot artifacts for a
 * BOM (bill-of-materials).
 */
public class LinkageMonitor {

  // Finding latest version requires metadata from remote repository
  private final RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();

  public static void main(String[] arguments)
      throws RepositoryException, IOException, LinkageMonitorException, MavenRepositoryException,
          ModelBuildingException {
    if (arguments.length < 1 || arguments[0].split(":").length != 2) {
      System.err.println(
          "Please specify BOM coordinates without version. Example:"
              + " com.google.cloud:libraries-bom");
      System.exit(1);
    }
    String bomCoordinates = arguments[0];
    List<String> coordinatesElements = Splitter.on(':').splitToList(bomCoordinates);

    new LinkageMonitor().run(coordinatesElements.get(0), coordinatesElements.get(1));
  }

  private void run(String groupId, String artifactId)
      throws RepositoryException, IOException, LinkageMonitorException, MavenRepositoryException,
          ModelBuildingException {
    String latestBomCoordinates =
        RepositoryUtility.findLatestCoordinates(repositorySystem, groupId, artifactId);
    System.out.println("BOM Coordinates: " + latestBomCoordinates);
    Bom baseline = RepositoryUtility.readBom(latestBomCoordinates);
    ImmutableSet<SymbolProblem> problemsInBaseline =
        LinkageChecker.create(baseline).findSymbolProblems().keySet();
    Bom snapshot = copyWithSnapshot(repositorySystem, baseline);

    // Comparing coordinates because DefaultArtifact does not override equals
    ImmutableList<String> baselineCoordinates = coordinatesList(baseline.getManagedDependencies());
    ImmutableList<String> snapshotCoordinates = coordinatesList(snapshot.getManagedDependencies());
    if (baselineCoordinates.equals(snapshotCoordinates)) {
      System.out.println(
          "Could not find SNAPSHOT versions for the artifacts in the BOM. "
              + "Not running comparison.");
      return;
    }

    ImmutableList<Artifact> snapshotManagedDependencies = snapshot.getManagedDependencies();
    LinkedListMultimap<Path, DependencyPath> jarToDependencyPaths =
        ClassPathBuilder.artifactsToDependencyPaths(snapshotManagedDependencies);
    ImmutableList<Path> classpath = ImmutableList.copyOf(jarToDependencyPaths.keySet());
    List<Path> entryPointJars = classpath.subList(0, snapshotManagedDependencies.size());

    ImmutableSetMultimap<SymbolProblem, ClassFile> snapshotSymbolProblems =
        LinkageChecker.create(classpath, ImmutableSet.copyOf(entryPointJars)).findSymbolProblems();
    ImmutableSet<SymbolProblem> problemsInSnapshot = snapshotSymbolProblems.keySet();

    if (problemsInBaseline.equals(problemsInSnapshot)) {
      System.out.println(
          "Snapshot versions have the same " + problemsInBaseline.size() + " errors as baseline");
      return;
    }

    Set<SymbolProblem> fixedProblems = Sets.difference(problemsInBaseline, problemsInSnapshot);
    if (!fixedProblems.isEmpty()) {
      System.out.println(messageForFixedErrors(fixedProblems));
    }
    Set<SymbolProblem> newProblems = Sets.difference(problemsInSnapshot, problemsInBaseline);
    if (!newProblems.isEmpty()) {
      System.err.println(
          messageForNewErrors(snapshotSymbolProblems, problemsInBaseline, jarToDependencyPaths));
      int errorSize = newProblems.size();
      throw new LinkageMonitorException(
          String.format("Found %d new linkage error%s", errorSize, errorSize > 1 ? "s" : ""));
    } else {
      // No new symbol problems introduced by snapshot BOM. Returning success.
      System.out.println("No new problem found");
    }
  }

  private static ImmutableList<String> coordinatesList(List<Artifact> artifacts) {
    return artifacts.stream().map(Artifacts::toCoordinates).collect(toImmutableList());
  }

  /**
   * Returns a message on {@code snapshotSymbolProblems} that do not exist in {@code
   * baselineProblems}.
   */
  @VisibleForTesting
  static String messageForNewErrors(
      ImmutableSetMultimap<SymbolProblem, ClassFile> snapshotSymbolProblems,
      Set<SymbolProblem> baselineProblems,
      LinkedListMultimap<Path, DependencyPath> jarToDependencyPaths) {
    Set<SymbolProblem> newProblems =
        Sets.difference(snapshotSymbolProblems.keySet(), baselineProblems);
    StringBuilder message =
        new StringBuilder("Newly introduced problem" + (newProblems.size() > 1 ? "s" : "") + ":\n");
    ImmutableSet.Builder<Path> problematicJars = ImmutableSet.builder();
    for (SymbolProblem problem : newProblems) {
      message.append(problem + "\n");
      for (ClassFile classFile : snapshotSymbolProblems.get(problem)) {
        message.append(
            String.format(
                "  referenced from %s (%s)\n",
                classFile.getClassName(), classFile.getJar().getFileName()));
        problematicJars.add(classFile.getJar());
      }
    }

    for (Path problematicJar : problematicJars.build()) {
      message.append(problematicJar.getFileName() + "is in path:");
      for (DependencyPath dependencyPath : jarToDependencyPaths.get(problematicJar)) {
        message.append("  " + dependencyPath);
      }
    }

    return message.toString();
  }

  /** Returns a message on {@code fixedProblems}. */
  @VisibleForTesting
  static String messageForFixedErrors(Set<SymbolProblem> fixedProblems) {
    int problemSize = fixedProblems.size();
    StringBuilder message =
        new StringBuilder(
            "The following problem"
                + (problemSize > 1 ? "s" : "")
                + " in the baseline no longer appear in the snapshot:\n");
    for (SymbolProblem problem : fixedProblems) {
      message.append("  " + problem + "\n");
    }
    return message.toString();
  }

  /**
   * Builds Maven model of {@code bomCoordinates} replacing its importing BOMs with
   * locally-installed snapshot versions. The replacement occurs between the two phases of the model
   * building.
   *
   * @see <a href="https://maven.apache.org/ref/3.6.1/maven-model-builder/">Maven Model Builder</a>
   */
  @VisibleForTesting
  static Model buildModelWithSnapshotBom(
      RepositorySystem repositorySystem, RepositorySystemSession session, String bomCoordinates)
      throws ModelBuildingException, ArtifactResolutionException, MavenRepositoryException {

    // BOM Coordinates might not have extension.
    String[] elements = bomCoordinates.split(":");
    DefaultArtifact bom;
    if (elements.length >= 4) {
      bom = new DefaultArtifact(bomCoordinates); // This may throw InvalidArgumentException
    } else if (elements.length == 3) {
      // When extension is not specified, use "pom"
      bom = new DefaultArtifact(elements[0], elements[1], "pom", elements[2]);
    } else {
      throw new IllegalArgumentException(
          "BOM coordinates do not have valid format: " + bomCoordinates);
    }

    ArtifactResult bomResult =
        repositorySystem.resolveArtifact(
            session, new ArtifactRequest(bom, ImmutableList.of(CENTRAL), null));

    ModelBuildingRequest modelRequest = new DefaultModelBuildingRequest();
    modelRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    modelRequest.setProcessPlugins(false);
    modelRequest.setTwoPhaseBuilding(true); // This forces the builder stop after phase 1
    modelRequest.setPomFile(bomResult.getArtifact().getFile());
    modelRequest.setModelResolver(
        new ProjectModelResolver(
            session,
            null,
            repositorySystem,
            new DefaultRemoteRepositoryManager(),
            ImmutableList.of(CENTRAL), // Needed when parent pom is not locally available
            null,
            null));
    // Profile activation needs JDK version through system properties
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/923
    modelRequest.setSystemProperties(System.getProperties());

    DefaultModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
    // Phase 1 done. Now variables are interpolated.
    ModelBuildingResult resultPhase1 = modelBuilder.build(modelRequest);
    DependencyManagement dependencyManagement =
        resultPhase1.getEffectiveModel().getDependencyManagement();

    for (org.apache.maven.model.Dependency dependency : dependencyManagement.getDependencies()) {
      // Replaces the versions of imported BOMs
      if ("import".equals(dependency.getScope())) {
        findSnapshotVersion(
                repositorySystem, session, dependency.getGroupId(), dependency.getArtifactId())
            .ifPresent(dependency::setVersion);
      }
    }

    // Phase 2 resolves dependency management imports
    ModelBuildingResult resultPhase2 = modelBuilder.build(modelRequest, resultPhase1);
    return resultPhase2.getEffectiveModel();
  }

  /**
   * Returns a copy of {@code bom} replacing its managed dependencies that have locally-installed
   * snapshot versions.
   */
  @VisibleForTesting
  static Bom copyWithSnapshot(RepositorySystem repositorySystem, Bom bom)
      throws MavenRepositoryException, ModelBuildingException, ArtifactResolutionException {
    ImmutableList.Builder<Artifact> managedDependencies = ImmutableList.builder();
    RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);

    Model model = buildModelWithSnapshotBom(repositorySystem, session, bom.getCoordinates());

    ArtifactTypeRegistry registry = session.getArtifactTypeRegistry();
    ImmutableList<Artifact> newManagedDependencies =
        model.getDependencyManagement().getDependencies().stream()
            .map(dependency -> RepositoryUtils.toDependency(dependency, registry))
            .map(Dependency::getArtifact)
            .collect(toImmutableList());
    for (Artifact managedDependency : newManagedDependencies) {
      if (RepositoryUtility.shouldSkipBomMember(managedDependency)) {
        continue;
      }
      managedDependencies.add(
          findSnapshotVersion(repositorySystem, session, managedDependency)
              .map(managedDependency::setVersion)
              .orElse(managedDependency));
    }
    // "-SNAPSHOT" suffix for coordinate to distinguish easily.
    return new Bom(bom.getCoordinates() + "-SNAPSHOT", managedDependencies.build());
  }

  private static Optional<String> findSnapshotVersion(
      RepositorySystem repositorySystem, RepositorySystemSession session, Artifact artifact)
      throws MavenRepositoryException {
    return findSnapshotVersion(
        repositorySystem, session, artifact.getGroupId(), artifact.getArtifactId());
  }

  /**
   * Returns {@code Optional} describing the highest snapshot version if such version is available
   * in {@code repositorySystem}; otherwise an empty {@code Optional}.
   */
  private static Optional<String> findSnapshotVersion(
      RepositorySystem repositorySystem,
      RepositorySystemSession session,
      String groupId,
      String artifactId)
      throws MavenRepositoryException {
    String version =
        RepositoryUtility.findHighestVersion(repositorySystem, session, groupId, artifactId);
    if (version.contains("-SNAPSHOT")) {
      return Optional.of(version);
    }
    return Optional.empty();
  }
}
