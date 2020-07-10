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
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.LinkageProblem;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
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
  private static final Logger logger = Logger.getLogger(LinkageMonitor.class.getName());

  private static final DefaultModelBuilder modelBuilder =
      new DefaultModelBuilderFactory().newInstance();

  // Finding latest version requires metadata from remote repository
  private final RepositorySystem repositorySystem = RepositoryUtility.newRepositorySystem();
  private final RepositorySystemSession session = RepositoryUtility.newSession(repositorySystem);
  private final ImmutableMap<String, String> localArtifacts =
      findLocalArtifacts(repositorySystem, session, Paths.get(".").toAbsolutePath());

  public static void main(String[] arguments)
      throws RepositoryException, IOException, MavenRepositoryException, ModelBuildingException {
    if (arguments.length < 1 || arguments[0].split(":").length != 2) {
      logger.severe(
          "Please specify BOM coordinates without version. Example:"
              + " com.google.cloud:libraries-bom");
      System.exit(1);
    }
    String bomCoordinates = arguments[0];
    List<String> coordinatesElements = Splitter.on(':').splitToList(bomCoordinates);

    Set<LinkageProblem> newLinkageProblems =
        new LinkageMonitor().run(coordinatesElements.get(0), coordinatesElements.get(1));
    int errorSize = newLinkageProblems.size();
    if (errorSize > 0) {
      logger.severe(
          String.format("Found %d new linkage error%s", errorSize, errorSize > 1 ? "s" : ""));
      logger.info(
          "For the details of the linkage errors, see "
              + "https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Messages");
      System.exit(1); // notify CI tools of the failure
    } else {
      logger.info("No new problem found");
    }
  }

  /**
   * Returns a map from versionless coordinates to version for all pom.xml found in {@code
   * projectDirectory}.
   */
  @VisibleForTesting
  static ImmutableMap<String, String> findLocalArtifacts(
      RepositorySystem repositorySystem, RepositorySystemSession session, Path projectDirectory) {
    ImmutableMap.Builder<String, String> artifactToVersion = ImmutableMap.builder();
    Iterable<Path> paths = MoreFiles.fileTraverser().breadthFirst(projectDirectory);

    for (Path path : paths) {
      if (!path.getFileName().endsWith("pom.xml")) {
        continue;
      }

      // This path element check should not depend on directory name outside the project
      Path relativePath = path.isAbsolute() ? projectDirectory.relativize(path) : path;
      ImmutableSet<Path> elements = ImmutableSet.copyOf(relativePath);
      if (elements.contains(Paths.get("build")) || elements.contains(Paths.get("target"))) {
        // Exclude Gradle's build directory and Maven's target directory, which would contain irrelevant pom.xml such as
        // gax/build/tmp/expandedArchives/(... omit ...)/META-INF/maven/org.jacoco/org.jacoco.agent/pom.xml
        continue;
      }

      ModelBuildingRequest modelRequest = new DefaultModelBuildingRequest();
      modelRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      modelRequest.setProcessPlugins(false);
      modelRequest.setTwoPhaseBuilding(false);
      modelRequest.setPomFile(path.toFile());
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

      try {
        ModelBuildingResult modelBuildingResult = modelBuilder.build(modelRequest);
        Model model = modelBuildingResult.getEffectiveModel();
        artifactToVersion.put(model.getGroupId() + ":" + model.getArtifactId(), model.getVersion());
      } catch (ModelBuildingException ex) {
        // Maven may fail to build pom.xml files found in irrelevant directories, such as "target"
        // and "test" directories of the project. Such failures can be ignored.
        logger.info("Ignoring bad model: " + path + ": " + ex.getMessage());
      }
    }
    return artifactToVersion.build();
  }

  /**
   * Returns new problems in the BOM specified by {@code groupId} and {@code artifactId}. This
   * method compares the latest release of the BOM and its snapshot version which uses artifacts in
   * {@link #localArtifacts}.
   */
  private ImmutableSet<LinkageProblem> run(String groupId, String artifactId)
      throws RepositoryException, IOException, MavenRepositoryException, ModelBuildingException {
    String latestBomCoordinates =
        RepositoryUtility.findLatestCoordinates(repositorySystem, groupId, artifactId);
    logger.info("BOM Coordinates: " + latestBomCoordinates);
    Bom baseline = Bom.readBom(latestBomCoordinates);
    ImmutableSet<LinkageProblem> problemsInBaseline =
        LinkageChecker.create(baseline, null).findSymbolProblems();
    Bom snapshot = copyWithSnapshot(repositorySystem, session, baseline, localArtifacts);

    // Comparing coordinates because DefaultArtifact does not override equals
    ImmutableList<String> baselineCoordinates = coordinatesList(baseline.getManagedDependencies());
    ImmutableList<String> snapshotCoordinates = coordinatesList(snapshot.getManagedDependencies());
    if (baselineCoordinates.equals(snapshotCoordinates)) {
      logger.info(
          "Snapshot is same as baseline. Not running comparison.");
      logger.info(
          "Baseline coordinates: " + Joiner.on(";").join(baselineCoordinates));
      return ImmutableSet.of();
    }

    ImmutableList<Artifact> snapshotManagedDependencies = snapshot.getManagedDependencies();
    ClassPathResult classPathResult = (new ClassPathBuilder()).resolve(snapshotManagedDependencies);
    ImmutableList<ClassPathEntry> classpath = classPathResult.getClassPath();
    List<ClassPathEntry> entryPointJars = classpath.subList(0, snapshotManagedDependencies.size());

    ImmutableSet<LinkageProblem> problemsInSnapshot =
        LinkageChecker.create(classpath, ImmutableSet.copyOf(entryPointJars), null)
            .findSymbolProblems();

    if (problemsInBaseline.equals(problemsInSnapshot)) {
      logger.info(
          "Snapshot versions have the same " + problemsInBaseline.size() + " errors as baseline");
      return ImmutableSet.of();
    }

    Set<LinkageProblem> fixedProblems = Sets.difference(problemsInBaseline, problemsInSnapshot);
    if (!fixedProblems.isEmpty()) {
      logger.info(messageForFixedErrors(fixedProblems));
    }

    Set<LinkageProblem> newProblems = Sets.difference(problemsInSnapshot, problemsInBaseline);
    if (!newProblems.isEmpty()) {
      logger.severe(messageForNewErrors(problemsInSnapshot, problemsInBaseline, classPathResult));
    }
    return ImmutableSet.copyOf(newProblems);
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
      Set<LinkageProblem> snapshotProblems,
      Set<LinkageProblem> baselineProblems,
      ClassPathResult classPathResult) {
    Set<LinkageProblem> newProblems = Sets.difference(snapshotProblems, baselineProblems);
    Builder<ClassPathEntry> problematicJars = ImmutableSet.builder();

    ImmutableListMultimap<String, LinkageProblem> groupedBySymbolProblem =
        Multimaps.index(newProblems, problem -> problem.formatSymbolProblem());
    StringBuilder message =
        new StringBuilder(
            "Newly introduced problem"
                + (groupedBySymbolProblem.keySet().size() > 1 ? "s" : "")
                + ":\n");
    for (String problem : groupedBySymbolProblem.keySet()) {
      message.append(problem + "\n");

      for (LinkageProblem linkageProblem : groupedBySymbolProblem.get(problem)) {
        // This is null for ClassNotFound error.
        ClassFile containingClass = linkageProblem.getContainingClass();
        if (containingClass != null) {
          problematicJars.add(containingClass.getClassPathEntry());
        }

        ClassFile sourceClass = linkageProblem.getSourceClass();
        message.append(
            String.format(
                "  referenced from %s (%s)\n",
                sourceClass.getBinaryName(), sourceClass.getClassPathEntry()));
        problematicJars.add(sourceClass.getClassPathEntry());
      }
    }

    message.append("\n");
    message.append(classPathResult.formatDependencyPaths(problematicJars.build()));

    return message.toString();
  }

  /** Returns a message on {@code fixedProblems}. */
  @VisibleForTesting
  static String messageForFixedErrors(Set<LinkageProblem> fixedProblems) {
    int problemSize = fixedProblems.size();
    StringBuilder message =
        new StringBuilder(
            "The following problem"
                + (problemSize > 1 ? "s" : "")
                + " in the baseline no longer appear in the snapshot:\n");
    for (LinkageProblem problem : fixedProblems) {
      message.append("  " + problem.formatSymbolProblem() + "\n");
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
      RepositorySystem repositorySystem,
      RepositorySystemSession session,
      String bomCoordinates,
      Map<String, String> localArtifacts)
      throws ModelBuildingException, ArtifactResolutionException {

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
        new VersionSubstitutingModelResolver(
            session,
            null,
            repositorySystem,
            new DefaultRemoteRepositoryManager(),
            ImmutableList.of(CENTRAL), // Needed when parent pom is not locally available
            localArtifacts));
    // Profile activation needs JDK version through system properties
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/923
    modelRequest.setSystemProperties(System.getProperties());

    // Phase 1 done. Now variables are interpolated.
    ModelBuildingResult resultPhase1 = modelBuilder.build(modelRequest);
    DependencyManagement dependencyManagement =
        resultPhase1.getEffectiveModel().getDependencyManagement();

    for (org.apache.maven.model.Dependency dependency : dependencyManagement.getDependencies()) {
      // Replaces the versions of imported BOMs
      if ("import".equals(dependency.getScope())) {
        String version =
            localArtifacts.getOrDefault(
                dependency.getGroupId() + ":" + dependency.getArtifactId(),
                dependency.getVersion());
        dependency.setVersion(version);
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
  static Bom copyWithSnapshot(
      RepositorySystem repositorySystem,
      RepositorySystemSession session,
      Bom bom,
      Map<String, String> localArtifacts)
      throws ModelBuildingException, ArtifactResolutionException {
    ImmutableList.Builder<Artifact> managedDependencies = ImmutableList.builder();

    Model model =
        buildModelWithSnapshotBom(repositorySystem, session, bom.getCoordinates(), localArtifacts);

    ArtifactTypeRegistry registry = session.getArtifactTypeRegistry();
    ImmutableList<Artifact> newManagedDependencies =
        model.getDependencyManagement().getDependencies().stream()
            .map(dependency -> RepositoryUtils.toDependency(dependency, registry))
            .map(Dependency::getArtifact)
            .collect(toImmutableList());
    for (Artifact managedDependency : newManagedDependencies) {
      if (Bom.shouldSkipBomMember(managedDependency)) {
        continue;
      }
      String version =
          localArtifacts.getOrDefault(
              managedDependency.getGroupId() + ":" + managedDependency.getArtifactId(),
              managedDependency.getVersion());
      managedDependencies.add(managedDependency.setVersion(version));
    }
    // "-SNAPSHOT" suffix for coordinate to distinguish easily.
    return new Bom(bom.getCoordinates() + "-SNAPSHOT", managedDependencies.build());
  }
}
