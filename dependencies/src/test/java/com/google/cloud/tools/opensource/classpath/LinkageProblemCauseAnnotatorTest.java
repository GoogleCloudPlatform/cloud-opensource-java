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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.DependencyGraphBuilder;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class LinkageProblemCauseAnnotatorTest {

  private ClassPathBuilder classPathBuilder = new ClassPathBuilder();

  @Test
  public void testAnnotate_dom4jOptionalDependency() throws IOException, RepositoryException {

    ClassPathBuilder builder = new ClassPathBuilder();
    ClassPathResult classPathResult =
        builder.resolve(
            ImmutableList.of(new DefaultArtifact("org.dom4j:dom4j:2.1.3")),
            false,
            DependencyMediation.MAVEN);

    // Dom4j declares jaxen dependency as optional. Dom4j's org.dom4j.DocumentHelper references
    // jaxen's org.jaxen.VariableContext. This annotator should tell that this invalid reference
    // is caused by the missing optional dependency.
    ImmutableList<ClassPathEntry> dom4jDependencies = TestHelper.resolve("org.dom4j:dom4j:2.1.3");
    ClassPathEntry dom4jEntry = dom4jDependencies.get(0);

    LinkageProblem problem =
        new ClassNotFoundProblem(
            new ClassFile(dom4jEntry, "org.dom4j.DocumentHelper"),
            new ClassSymbol("org.jaxen.VariableContext"));

    LinkageProblemCauseAnnotator.annotate(
        classPathBuilder, classPathResult, ImmutableSet.of(problem));

    LinkageProblemCause cause = problem.getCause();
    assertEquals(MissingDependency.class, cause.getClass());
    DependencyPath pathToMissingArtifact = ((MissingDependency) cause).getPathToMissingArtifact();
    Artifact leaf = pathToMissingArtifact.getLeaf();
    assertEquals("jaxen", leaf.getArtifactId());
  }

  @Test
  public void testAnnotate_googleApiClientAndGrpcConflict()
      throws IOException, RepositoryException {

    // The google-api-client and grpc-core have dependency conflict on Guava's Verify.verify
    // method.
    ClassPathBuilder builder = new ClassPathBuilder();
    ClassPathResult classPathResult =
        builder.resolve(
            ImmutableList.of(
                new DefaultArtifact("com.google.api-client:google-api-client:1.27.0"),
                new DefaultArtifact("io.grpc:grpc-core:1.17.1")),
            false,
            DependencyMediation.MAVEN);

    LinkageChecker linkageChecker = LinkageChecker.create(classPathResult.getClassPath());
    ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

    Optional<LinkageProblem> foundProblem =
        linkageProblems.stream()
            .filter(problem -> problem instanceof SymbolNotFoundProblem)
            .findFirst();

    SymbolNotFoundProblem problem = (SymbolNotFoundProblem) foundProblem.get();
    assertEquals("verify", ((MethodSymbol) problem.getSymbol()).getName());

    LinkageProblemCauseAnnotator.annotate(
        classPathBuilder, classPathResult, ImmutableSet.of(problem));

    LinkageProblemCause cause = problem.getCause();
    assertTrue(cause instanceof DependencyConflict);
    DependencyPath pathToSelectedArtifact =
        ((DependencyConflict) cause).getPathToSelectedArtifact();
    Artifact selectedLeaf = pathToSelectedArtifact.getLeaf();
    assertEquals("guava", selectedLeaf.getArtifactId());
    assertEquals("20.0", selectedLeaf.getVersion());
    Artifact firstElementInSelected = pathToSelectedArtifact.get(1);
    assertEquals("google-api-client", firstElementInSelected.getArtifactId());

    DependencyPath pathToUnselectedArtifact =
        ((DependencyConflict) cause).getPathToArtifactThruSource();
    Artifact unselectedLeaf = pathToUnselectedArtifact.getLeaf();
    assertEquals("guava", unselectedLeaf.getArtifactId());
    assertEquals("26.0-android", unselectedLeaf.getVersion());
    Artifact firstElementInUnselected = pathToUnselectedArtifact.get(1);
    assertEquals("grpc-core", firstElementInUnselected.getArtifactId());
  }

  @Test
  public void testAnnotate_autoServiceAnnotationsExclusion()
      throws IOException, RepositoryException {

    // Auto-value declares exclusion element for auto-service-annotations. Because of this, the
    // dependency graph does not include auto-service-annotations, resulting in a linkage error for
    // AutoServiceProcessor class.
    ClassPathBuilder builder = new ClassPathBuilder();
    ClassPathResult classPathResult =
        builder.resolve(
            ImmutableList.of(new DefaultArtifact("com.google.auto.value:auto-value:1.7.3")),
            false,
            DependencyMediation.MAVEN);

    Optional<ClassPathEntry> foundAutoService =
        classPathResult.getClassPath().stream()
            .filter(entry -> entry.getArtifact().getArtifactId().equals("auto-service"))
            .findFirst();
    ClassPathEntry autoServiceEntry = foundAutoService.get();

    LinkageProblem problem =
        new ClassNotFoundProblem(
            new ClassFile(
                autoServiceEntry, "com.google.auto.service.processor.AutoServiceProcessor"),
            new ClassSymbol("com.google.auto.service.AutoService"));

    LinkageProblemCauseAnnotator.annotate(
        classPathBuilder, classPathResult, ImmutableSet.of(problem));

    LinkageProblemCause cause = problem.getCause();
    assertEquals(ExcludedDependency.class, cause.getClass());
    ExcludedDependency excludedDependency = (ExcludedDependency) cause;
    DependencyPath pathToMissingArtifact = excludedDependency.getPathToMissingArtifact();
    Artifact leaf = pathToMissingArtifact.getLeaf();
    assertEquals("auto-service-annotations", leaf.getArtifactId());

    assertEquals("auto-value", excludedDependency.getExcludingArtifact().getArtifactId());
  }

  @Test
  public void testAnnotate_dependencyInSpringRepository() throws IOException, RepositoryException {
    DependencyGraphBuilder dependencyGraphBuilder =
        new DependencyGraphBuilder(
            ImmutableList.of(
                "https://repo.spring.io/milestone", RepositoryUtility.CENTRAL.getUrl()));

    ClassPathBuilder classPathBuilderWithSpring = new ClassPathBuilder(dependencyGraphBuilder);

    // io.projectreactor:reactor-core:3.4.0-M2 is in the Spring Milestones repository.
    ClassPathResult classPathResult =
        classPathBuilderWithSpring.resolve(
            ImmutableList.of(
                new DefaultArtifact("io.projectreactor:reactor-core:3.4.0-M2"),
                new DefaultArtifact("org.reactivestreams:reactive-streams:0.4.0")),
            false,
            DependencyMediation.MAVEN);

    ClassPathEntry reactorCore = classPathResult.getClassPath().get(0);

    // A hypothetical problem where org.reactivestreams.Subscriber class is missing because the
    // org.reactivestreams:reactive-streams, which contains the class, has a different version.
    LinkageProblem problem =
        new ClassNotFoundProblem(
            new ClassFile(reactorCore, "reactor.util.Metrics"),
            new ClassSymbol("org.reactivestreams.Subscriber"));

    // This should not throw exception in resolving dependency graph
    LinkageProblemCauseAnnotator.annotate(
        classPathBuilderWithSpring, classPathResult, ImmutableSet.of(problem));

    LinkageProblemCause cause = problem.getCause();
    assertEquals(DependencyConflict.class, cause.getClass());
    DependencyConflict conflict = (DependencyConflict) cause;

    assertEquals(
        "org.reactivestreams:reactive-streams:0.4.0",
        Artifacts.toCoordinates(conflict.getPathToSelectedArtifact().getLeaf()));

    // io.projectreactor:reactor-core depends on reactive-streams 1.0.3
    assertEquals(
        "org.reactivestreams:reactive-streams:1.0.3",
        Artifacts.toCoordinates(conflict.getPathToArtifactThruSource().getLeaf()));
  }
}
