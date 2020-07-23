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

package com.google.cloud.tools.dependencies.enforcer;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static org.apache.maven.enforcer.rule.api.EnforcerLevel.WARN;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.ClassReferenceGraph;
import com.google.cloud.tools.opensource.classpath.IncompatibleLinkageProblem;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.LinkageProblem;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyGraph;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.FilteringZipDependencySelector;
import com.google.cloud.tools.opensource.dependencies.NonTestDependencySelector;
import com.google.cloud.tools.opensource.dependencies.OsProperties;
import com.google.cloud.tools.opensource.dependencies.UnresolvableArtifactProblem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.enforcer.AbstractNonCacheableEnforcerRule;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;

/** Linkage Checker Maven Enforcer Rule. */
public class LinkageCheckerRule extends AbstractNonCacheableEnforcerRule {

  /**
   * Maven packaging values known to be irrelevant to Linkage Check for non-BOM project.
   *
   * @see <a href="https://maven.apache.org/ref/current/maven-core/artifact-handlers.html">Maven
   * Core: Default Artifact Handlers Reference</a>
   */
  private static final ImmutableSet<String> UNSUPPORTED_NONBOM_PACKAGING = ImmutableSet.of("pom",
      "java-source", "javadoc");

  /**
   * The section this rule reads dependencies from. By default, it's {@link
   * DependencySection#DEPENDENCIES}.
   */
  private DependencySection dependencySection = DependencySection.DEPENDENCIES;

  /**
   * Set to true to suppress linkage errors unreachable from the classes in the direct dependencies.
   * By default, it's {@code false}.
   *
   * @see <a href=
   *     "https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#class-reference-graph"
   *     >Java Dependency Glossary: Class reference graph</a>
   */
  private boolean reportOnlyReachable = false;

  private String exclusionFile = null;

  private ClassPathBuilder classPathBuilder = new ClassPathBuilder();

  @VisibleForTesting
  void setDependencySection(DependencySection dependencySection) {
    this.dependencySection = dependencySection;
  }

  @VisibleForTesting
  void setReportOnlyReachable(boolean reportOnlyReachable) {
    this.reportOnlyReachable = reportOnlyReachable;
  }

  @VisibleForTesting
  enum DependencySection {
    /** To read {@code dependencyManagement} section in pom.xml. This is for BOM projects */
    DEPENDENCY_MANAGEMENT,
    /** To read {@code dependencies} section in pom.xml. This is for library users' projects */
    DEPENDENCIES
  }

  @VisibleForTesting
  void setExclusionFile(String exclusionFile) {
    this.exclusionFile = exclusionFile;
  }

  private static Log logger;

  @Override
  public void execute(@Nonnull EnforcerRuleHelper helper) throws EnforcerRuleException {
    logger = helper.getLog();

    try {
      MavenProject project = (MavenProject) helper.evaluate("${project}");
      MavenSession session = (MavenSession) helper.evaluate("${session}");
      MojoExecution execution = (MojoExecution) helper.evaluate("${mojoExecution}");
      RepositorySystemSession repositorySystemSession = session.getRepositorySession();

      boolean readingDependencyManagementSection =
          dependencySection == DependencySection.DEPENDENCY_MANAGEMENT;
      if (readingDependencyManagementSection
          && (project.getDependencyManagement() == null
              || project.getDependencyManagement().getDependencies() == null
              || project.getDependencyManagement().getDependencies().isEmpty())) {
        logger.warn("The rule is set to read dependency management section but it is empty.");
      }

      String projectType = project.getArtifact().getType();
      if (readingDependencyManagementSection) {
        if (!"pom".equals(projectType)) {
          logger.warn("A BOM should have packaging pom");
          return;
        }
      } else {
        if (UNSUPPORTED_NONBOM_PACKAGING.contains(projectType)) {
          return;
        }
        if (!"verify".equals(execution.getLifecyclePhase())) {
          throw new EnforcerRuleException(
              "To run the check on the compiled class files, the linkage checker enforcer rule"
                  + " should be bound to the 'verify' phase. Current phase: "
                  + execution.getLifecyclePhase());
        }
        if (project.getArtifact().getFile() == null) {
          // Skipping projects without a file, such as Guava's guava-tests module.
          // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/850
          return;
        }
      }

      ClassPathResult classPathResult =
          readingDependencyManagementSection
              ? findBomClasspath(project, repositorySystemSession)
              : findProjectClasspath(project, repositorySystemSession, helper);
      ImmutableList<ClassPathEntry> classPath = classPathResult.getClassPath();
      if (classPath.isEmpty()) {
        logger.warn("Class path is empty.");
        return;
      }

      // As sorted by level order, the first elements in classpath are the project and its direct
      // non-test dependencies.
      List<org.apache.maven.model.Dependency> dependencies = project.getDependencies();
      long projectDependencyCount =
          dependencies.stream().filter(dependency -> !"test".equals(dependency.getScope())).count();
      List<ClassPathEntry> entryPoints = classPath.subList(0, (int) projectDependencyCount + 1);

      try {

        // TODO LinkageChecker.create and LinkageChecker.findLinkageProblems
        // should not be two separate public methods since we always call
        // findLinkageProblems immediately after create.

        Path exclusionFile = this.exclusionFile == null ? null : Paths.get(this.exclusionFile);
        LinkageChecker linkageChecker =
            LinkageChecker.create(classPath, entryPoints, exclusionFile);
        ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();
        if (reportOnlyReachable) {
          ClassReferenceGraph classReferenceGraph = linkageChecker.getClassReferenceGraph();
          linkageProblems =
              linkageProblems.stream()
                  .filter(
                      entry ->
                          classReferenceGraph.isReachable(entry.getSourceClass().getBinaryName()))
                  .collect(toImmutableSet());
        }
        // Count unique LinkageProblems by their symbols
        long errorCount =
            linkageProblems.stream().map(LinkageProblem::formatSymbolProblem).distinct().count();

        String foundError = reportOnlyReachable ? "reachable error" : "error";
        if (errorCount > 1) {
          foundError += "s";
        }
        if (errorCount > 0) {
          String message =
              String.format(
                  "Linkage Checker rule found %d %s. Linkage error report:\n%s",
                  errorCount, foundError, LinkageProblem.formatLinkageProblems(linkageProblems));
          String dependencyPaths =
              dependencyPathsOfProblematicJars(classPathResult, linkageProblems);

          if (getLevel() == WARN) {
            logger.warn(message);
            logger.warn(dependencyPaths);
          } else {
            logger.error(message);
            logger.error(dependencyPaths);
            logger.info(
                "For the details of the linkage errors, see "
                    + "https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Messages");
            throw new EnforcerRuleException(
                "Failed while checking class path. See above error report.");
          }
        } else {
          // arguably shouldn't log anything on success
          logger.info("No " + foundError + " found");
        }
      } catch (IOException ex) {
        // Maven's "-e" flag does not work for EnforcerRuleException. Print stack trace here.
        logger.warn("Failed to run Linkage Checker", ex);
        return; // Not failing the build.
      }
    } catch (ExpressionEvaluationException ex) {
      throw new EnforcerRuleException("Unable to lookup an expression " + ex.getMessage(), ex);
    }
  }

  /** Builds a class path for {@code mavenProject}. */
  private static ClassPathResult findProjectClasspath(
      MavenProject mavenProject, RepositorySystemSession session, EnforcerRuleHelper helper)
      throws EnforcerRuleException {
    try {
      ProjectDependenciesResolver projectDependenciesResolver =
          helper.getComponent(ProjectDependenciesResolver.class);
      DefaultRepositorySystemSession fullDependencyResolutionSession =
          new DefaultRepositorySystemSession(session);

      // Clear artifact cache. Certain artifacts in the cache have dependencies without
      // ${os.detected.classifier} interpolated. They are instantiated before 'verify' phase:
      // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/925
      fullDependencyResolutionSession.setCache(new DefaultRepositoryCache());

      // For netty-handler referencing its dependencies with ${os.detected.classifier}
      Map<String, String> properties = new HashMap<>(); // allowing duplicate entries
      properties.putAll(fullDependencyResolutionSession.getSystemProperties());
      properties.putAll(OsProperties.detectOsProperties());
      fullDependencyResolutionSession.setSystemProperties(properties);

      fullDependencyResolutionSession.setDependencySelector(
          new AndDependencySelector(
              new NonTestDependencySelector(),
              new ExclusionDependencySelector(),
              new FilteringZipDependencySelector()));
      DependencyResolutionRequest dependencyResolutionRequest =
          new DefaultDependencyResolutionRequest(mavenProject, fullDependencyResolutionSession);

      DependencyResolutionResult resolutionResult =
          projectDependenciesResolver.resolve(dependencyResolutionRequest);

      return buildClassPathResult(resolutionResult);
    } catch (ComponentLookupException e) {
      throw new EnforcerRuleException("Unable to lookup a component " + e.getMessage(), e);
    } catch (DependencyResolutionException e) {
      return buildClasspathFromException(e);
    }
  }

  /** Returns class path built from partial dependency graph of {@code resolutionException}. */
  private static ClassPathResult buildClasspathFromException(
      DependencyResolutionException resolutionException) throws EnforcerRuleException {
    DependencyResolutionResult result = resolutionException.getResult();

    for (Throwable cause = resolutionException.getCause();
        cause != null;
        cause = cause.getCause()) {
      if (cause instanceof ArtifactTransferException) {
        
        DependencyNode root = result.getDependencyGraph();
        DependencyGraph graph = new DependencyGraph(root);
        
        ArtifactTransferException artifactException = (ArtifactTransferException) cause;
        Artifact artifact = artifactException.getArtifact();
        String warning = graph.createUnresolvableArtifactProblem(artifact).toString();
        logger.warn(warning);
        break;
      }
    }
    if (result.getResolvedDependencies().isEmpty()) {
      // Nothing is resolved. Probably failed at collection phase before resolve phase.
      throw new EnforcerRuleException("Unable to collect dependencies", resolutionException);
    } else {
      // The exception is acceptable enough to build a class path.
      return buildClassPathResult(result);
    }
  }

  private static ClassPathResult buildClassPathResult(DependencyResolutionResult result)
      throws EnforcerRuleException {
    // The root node must have the project's JAR file
    DependencyNode root = result.getDependencyGraph();
    File rootFile = root.getArtifact().getFile();
    if (rootFile == null) {
      throw new EnforcerRuleException("The root project artifact is not associated with a file.");
    }

    List<Dependency> unresolvedDependencies = result.getUnresolvedDependencies();
    Set<Artifact> unresolvedArtifacts =
        unresolvedDependencies.stream().map(Dependency::getArtifact).collect(toImmutableSet());

    DependencyGraph dependencyGraph = DependencyGraph.from(root);
    ImmutableListMultimap.Builder<ClassPathEntry, DependencyPath> builder =
        ImmutableListMultimap.builder();
    ImmutableList.Builder<UnresolvableArtifactProblem> problems = ImmutableList.builder();
    for (DependencyPath path : dependencyGraph.list()) {
      Artifact artifact = path.getLeaf();

      if (unresolvedArtifacts.contains(artifact)) {
        problems.add(new UnresolvableArtifactProblem(artifact));
      } else {
        builder.put(new ClassPathEntry(artifact), path);
      }
    }
    return new ClassPathResult(builder.build(), problems.build());
  }

  /** Builds a class path for {@code bomProject}. */
  private ClassPathResult findBomClasspath(
      MavenProject bomProject, RepositorySystemSession repositorySystemSession)
      throws EnforcerRuleException {

    ArtifactTypeRegistry artifactTypeRegistry = repositorySystemSession.getArtifactTypeRegistry();
    ImmutableList<Artifact> artifacts =
        bomProject.getDependencyManagement().getDependencies().stream()
            .map(dependency -> RepositoryUtils.toDependency(dependency, artifactTypeRegistry))
            .map(Dependency::getArtifact)
            .filter(artifact -> !Bom.shouldSkipBomMember(artifact))
            .collect(toImmutableList());

    ClassPathResult result = classPathBuilder.resolve(artifacts, false);
    ImmutableList<UnresolvableArtifactProblem> artifactProblems = result.getArtifactProblems();
    if (!artifactProblems.isEmpty()) {
      throw new EnforcerRuleException("Failed to collect dependency: " + artifactProblems);
    }
    return result;
  }

  private String dependencyPathsOfProblematicJars(
      ClassPathResult classPathResult, Set<LinkageProblem> linkageProblems) {
    ImmutableSet.Builder<ClassPathEntry> problematicJars = ImmutableSet.builder();
    for (LinkageProblem problem : linkageProblems) {
      if (problem instanceof IncompatibleLinkageProblem) {
        problematicJars.add(
            ((IncompatibleLinkageProblem) problem).getTargetClass().getClassPathEntry());
      }

      ClassFile sourceClass = problem.getSourceClass();
      problematicJars.add(sourceClass.getClassPathEntry());
    }

    return "Problematic artifacts in the dependency tree:\n"
        + classPathResult.formatDependencyPaths(problematicJars.build());
  }
}
