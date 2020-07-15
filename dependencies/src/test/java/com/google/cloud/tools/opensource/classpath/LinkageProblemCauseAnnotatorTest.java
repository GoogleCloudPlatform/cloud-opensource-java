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

import static com.google.cloud.tools.opensource.classpath.TestHelper.resolve;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

public class LinkageProblemCauseAnnotatorTest {

  @Test
  public void testAnnotate_dom4jOptionalDependency() throws IOException {

    ClassPathBuilder builder = new ClassPathBuilder();
    ClassPathResult classPathResult =
        builder.resolve(ImmutableList.of(new DefaultArtifact("org.dom4j:dom4j:2.1.3")), false);

    // Dom4j declares jaxen dependency as optional. Dom4j's org.dom4j.DocumentHelper references
    // jaxen's org.jaxen.VariableContext. This annotator should tell that this invalid reference
    // is caused by the missing optional dependency.
    ImmutableList<ClassPathEntry> dom4jDependencies = resolve("org.dom4j:dom4j:2.1.3");
    ClassPathEntry dom4jEntry = dom4jDependencies.get(0);

    LinkageProblem problem =
        new ClassNotFoundProblem(
            new ClassFile(dom4jEntry, "org.dom4j.DocumentHelper"),
            new ClassSymbol("org.jaxen.VariableContext"));

    LinkageProblemCauseAnnotator.annotate(classPathResult, ImmutableSet.of(problem));

    LinkageProblemCause cause = problem.getCause();
    assertEquals(MissingDependency.class, cause.getClass());
    DependencyPath pathToMissingArtifact = ((MissingDependency) cause).getPathToMissingArtifact();
    Artifact leaf = pathToMissingArtifact.getLeaf();
    assertEquals("jaxen", leaf.getArtifactId());
  }

  @Test
  public void testAnnotate_parquetProvidedDependency() throws IOException {

    // The parquet-hadoop's classes reference org.apache.hadoop.conf.Configuration. The artifact
    // depends on hadoop-client with 'provided' scope. The hadoop-client depends on
    // hadoop-common, which contains org.apache.hadoop.conf.Configuration class.
    ClassPathBuilder builder = new ClassPathBuilder();
    ClassPathResult classPathResult =
        builder.resolve(
            ImmutableList.of(
                new DefaultArtifact("org.apache.beam:beam-sdks-java-io-parquet:2.22.0")),
            false);

    ImmutableList<ClassPathEntry> classPath = classPathResult.getClassPath();
    LinkageChecker linkageChecker = LinkageChecker.create(classPath);
    ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

    Optional<LinkageProblem> foundProblem =
        linkageProblems.stream()
            .filter(
                problem ->
                    problem
                        .getSymbol()
                        .equals(new ClassSymbol("org.apache.hadoop.conf.Configuration")))
            .filter(
                problem ->
                    problem
                        .getSourceClass()
                        .getBinaryName()
                        .equals("org.apache.parquet.avro.AvroParquetReader"))
            .findFirst();
    LinkageProblem problem = foundProblem.get();

    LinkageProblemCauseAnnotator.annotate(classPathResult, ImmutableSet.of(problem));

    LinkageProblemCause cause = problem.getCause();
    assertEquals(MissingDependency.class, cause.getClass());
    DependencyPath pathToMissingArtifact = ((MissingDependency) cause).getPathToMissingArtifact();
    Artifact leaf = pathToMissingArtifact.getLeaf();
    assertEquals("hadoop-common", leaf.getArtifactId());
    assertEquals(
        "org.apache.beam:beam-sdks-java-io-parquet:jar:2.22.0 / "
            + "org.apache.parquet:parquet-avro:1.10.0 (compile) / "
            + "org.apache.hadoop:hadoop-client:2.7.3 (provided) / "
            +
            // hadoop-client depends on hadoop-common with "compile" scope. But this is overwritten
            // by hadoop-common's "provided" scope by parquet-avro.
            "org.apache.hadoop:hadoop-common:2.7.3 (provided)",
        pathToMissingArtifact.toString());
  }

  @Test
  public void testAnnotate_googleApiClientAndGrpcConflict() throws IOException {

    // The google-api-client and grpc-core have dependency conflict on Guava's Verify.verify
    // method.
    ClassPathBuilder builder = new ClassPathBuilder();
    ClassPathResult classPathResult =
        builder.resolve(
            ImmutableList.of(
                new DefaultArtifact("com.google.api-client:google-api-client:1.27.0"),
                new DefaultArtifact("io.grpc:grpc-core:1.17.1")),
            false);

    LinkageChecker linkageChecker = LinkageChecker.create(classPathResult.getClassPath());
    ImmutableSet<LinkageProblem> linkageProblems = linkageChecker.findLinkageProblems();

    Optional<LinkageProblem> foundProblem =
        linkageProblems.stream()
            .filter(problem -> problem instanceof SymbolNotFoundProblem)
            .findFirst();

    SymbolNotFoundProblem problem = (SymbolNotFoundProblem) foundProblem.get();
    assertEquals("verify", ((MethodSymbol) problem.getSymbol()).getName());

    LinkageProblemCauseAnnotator.annotate(classPathResult, ImmutableSet.of(problem));

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
        ((DependencyConflict) cause).getPathToUnselectedArtifact();
    Artifact unselectedLeaf = pathToUnselectedArtifact.getLeaf();
    assertEquals("guava", unselectedLeaf.getArtifactId());
    assertEquals("26.0-android", unselectedLeaf.getVersion());
    Artifact firstElementInUnselected = pathToUnselectedArtifact.get(1);
    assertEquals("grpc-core", firstElementInUnselected.getArtifactId());
  }
}
