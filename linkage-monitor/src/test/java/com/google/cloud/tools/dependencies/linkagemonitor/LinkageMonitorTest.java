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

import static com.google.cloud.tools.opensource.dependencies.Artifacts.toCoordinates;
import static com.google.common.collect.Iterables.skip;
import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassNotFoundProblem;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.classpath.ClassSymbol;
import com.google.cloud.tools.opensource.classpath.LinkageProblem;
import com.google.cloud.tools.opensource.classpath.MethodSymbol;
import com.google.cloud.tools.opensource.classpath.SymbolNotFoundProblem;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.Before;
import org.junit.Test;

public class LinkageMonitorTest {
  
  private RepositorySystem system;
  private RepositorySystemSession session;

  private Artifact artifactA =
      new DefaultArtifact("foo:a:1.2.3").setFile(new File("foo/a-1.2.3.jar"));
  private ClassPathEntry jarA = new ClassPathEntry(artifactA);

  private Artifact artifactB = new DefaultArtifact("foo:b:1.0.0")
      .setFile(new File("foo/b-1.0.0.jar"));
  private ClassPathEntry jarB = new ClassPathEntry(artifactB);

  private LinkageProblem classNotFoundProblem =
      new ClassNotFoundProblem(
          new ClassFile(jarA, "com.abc.AAA"), new ClassSymbol("java.lang.Integer"));
  private LinkageProblem methodNotFoundProblemFromA;
  private LinkageProblem methodNotFoundProblemFromB;

  @Before
  public void setup() throws IOException {
    system = RepositoryUtility.newRepositorySystem();
    session = RepositoryUtility.newSession(system);

    methodNotFoundProblemFromA =
        new SymbolNotFoundProblem(
            new ClassFile(jarA, "com.abc.AAA"),
            new ClassFile(jarB, "io.grpc.protobuf.ProtoUtils"),
            new MethodSymbol(
                "io.grpc.protobuf.ProtoUtils",
                "marshaller",
                "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
                false));

    methodNotFoundProblemFromB =
        new SymbolNotFoundProblem(
            new ClassFile(jarA, "com.abc.BBB"),
            new ClassFile(jarB, "io.grpc.protobuf.ProtoUtils"),
            new MethodSymbol(
                "io.grpc.protobuf.ProtoUtils",
                "marshaller",
                "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
                false));
  }
  
  @Test
  public void testCommandLine() {
    String[] arguments = {"something"};
    LinkageMonitor.parseCommandLine(arguments);
  }

  @Test
  public void testMain()
      throws RepositoryException, IOException, MavenRepositoryException, ModelBuildingException {
    String[] arguments = {"com.google.guava:guava-bom"};
    LinkageMonitor.main(arguments);
  }

  @Test
  public void testBomSnapshot()
      throws ModelBuildingException, ArtifactResolutionException, ArtifactDescriptorException {

    Bom bom = Bom.readBom("com.google.cloud:libraries-bom:1.2.0");
    Bom snapshotBom =
        LinkageMonitor.copyWithSnapshot(
            system,
            session,
            bom,
            ImmutableMap.of("com.google.protobuf:protobuf-java", "3.8.0-SNAPSHOT"));

    assertWithMessage(
            "The first element of the SNAPSHOT BOM should be the same as the original BOM")
        .that(toCoordinates(snapshotBom.getManagedDependencies().get(0)))
        .isEqualTo("com.google.protobuf:protobuf-java:3.8.0-SNAPSHOT");

    assertWithMessage("Artifacts other than protobuf-java should have the original version")
        .that(skip(snapshotBom.getManagedDependencies(), 1))
        .comparingElementsUsing(
            transforming(
                Artifacts::toCoordinates,
                Artifacts::toCoordinates,
                "has the same Maven coordinates as"))
        .containsExactlyElementsIn(skip(bom.getManagedDependencies(), 1))
        .inOrder();
  }

  @Test
  public void generateMessageForNewError() throws IOException {
    Set<LinkageProblem> baselineProblems = ImmutableSet.of(classNotFoundProblem);

    ImmutableSet<LinkageProblem> snapshotProblems =
        ImmutableSet.of(
            classNotFoundProblem, // This is in baseline. It should not be printed
            methodNotFoundProblemFromA,
            methodNotFoundProblemFromB);

    DependencyPath pathToA =
        new DependencyPath(new DefaultArtifact("foo:bar:1.0.0"))
            .append(
                new org.eclipse.aether.graph.Dependency(
                    new DefaultArtifact("foo:a:1.2.3"), "compile", true));
    DependencyPath pathToB = new DependencyPath(new DefaultArtifact("foo:b:1.0.0"));

    String message =
        LinkageMonitor.messageForNewErrors(
            snapshotProblems,
            baselineProblems,
            new ClassPathResult(
                ImmutableListMultimap.of(jarA, pathToA, jarB, pathToB),
                ImmutableList.of()));
    assertEquals(
        "Newly introduced problem:\n"
            + "(foo:b:1.0.0) io.grpc.protobuf.ProtoUtils's method"
            + " marshaller(com.google.protobuf.Message) is not found\n"
            + "  referenced from com.abc.AAA (foo:a:1.2.3)\n"
            + "  referenced from com.abc.BBB (foo:a:1.2.3)\n"
            + "\n"
            + "foo:b:1.0.0 is at:\n"
            + "  foo:b:jar:1.0.0\n"
            + "foo:a:1.2.3 is at:\n"
            + "  foo:bar:jar:1.0.0 / foo:a:1.2.3 (compile, optional)\n",
        message);
  }

  @Test
  public void testGenerateMessageForFixedError() {
    String message =
        LinkageMonitor.messageForFixedErrors(
            ImmutableSet.of(classNotFoundProblem, methodNotFoundProblemFromA));
    assertEquals(
        "The following problems in the baseline no longer appear in the snapshot:\n"
            + "  Class java.lang.Integer is not found\n"
            + "  (foo:b:1.0.0) io.grpc.protobuf.ProtoUtils's method "
            + "marshaller(com.google.protobuf.Message) is not found\n",
        message);
  }

  @Test
  public void testBuildModelWithSnapshotBom_noSnapshotUpdate()
      throws ModelBuildingException, ArtifactResolutionException {
    Model model =
        LinkageMonitor.buildModelWithSnapshotBom(
            system, session, "com.google.cloud:libraries-bom:2.2.1", ImmutableMap.of());
    List<Dependency> dependencies = model.getDependencyManagement().getDependencies();

    assertEquals(
        "There should not be a duplicate",
        dependencies.stream().distinct().count(),
        dependencies.size());

    // This number is different from the number appearing in BOM dashboard because model from
    // buildModelWithSnapshotBom still contains unnecessary artifacts that would be filtered by
    // RepositoryUtility.shouldSkipBomMember
    assertEquals(214, dependencies.size());
  }

  @Test
  public void testBuildModelWithSnapshotBom_invalidCoordinates()
      throws ModelBuildingException, ArtifactResolutionException {
    for (String invalidCoordinates : ImmutableList.of("a.b.c:d", "a:b:c:d:e:1", "a::c:0.1")) {
      try {
        LinkageMonitor.buildModelWithSnapshotBom(
            system, session, invalidCoordinates, ImmutableMap.of());
        fail("The method should invalidate coordinates: " + invalidCoordinates);
      } catch (IllegalArgumentException ex) {
        // pass
      }
    }
  }

  @Test
  public void testBuildModelWithSnapshotBom_BomSnapshotUpdate()
      throws ModelBuildingException, ArtifactResolutionException {
    // Linkage Monitor should update a BOM in Google Cloud Libraries BOM when it's available local
    // repository. This test case simulates the issue below where
    // google-cloud-bom:0.106.0-alpha-SNAPSHOT should provide gax:1.48.0.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/853
    // Libraries-bom:2.2.1 has google-cloud-bom:0.91.0-alpha, which has gax:1.44.0
    Model model =
        LinkageMonitor.buildModelWithSnapshotBom(
            system,
            session,
            "com.google.cloud:libraries-bom:2.2.1",
            ImmutableMap.of("com.google.cloud:google-cloud-bom", "0.106.0-alpha"));
    List<Dependency> dependencies = model.getDependencyManagement().getDependencies();

    // google-cloud-bom:0.106.0 has new artifacts such as google-cloud-gameservices
    assertEquals(224, dependencies.size());

    // google-cloud-bom:0.106.0-alpha has gax:1.48.0
    assertTrue(
        dependencies.stream()
            .anyMatch(
                dependency ->
                    "gax".equals(dependency.getArtifactId())
                        && "1.48.0".equals(dependency.getVersion())));
  }

  @Test
  public void testBuildModelWithSnapshotBom_JdkVersionActivation()
      throws ModelBuildingException, ArtifactResolutionException {
    // google-cloud-core-parent's parent google-cloud-shared-config uses JDK version to activate
    // a profile. Without JDK system property, this throws ModelBuildingException.
    Model model =
        LinkageMonitor.buildModelWithSnapshotBom(
            system, session, "com.google.cloud:google-cloud-core-parent:1.91.0", ImmutableMap.of());
    assertNotNull(model);
  }

  @Test
  public void testFindLocalArtifacts() {
    ImmutableMap<String, String> localArtifacts =
        LinkageMonitor.findLocalArtifacts(
            system, session, Paths.get("src/test/resources/testproject"));

    // This should not include project under "build" directory
    Truth.assertThat(localArtifacts).hasSize(2);
    Truth.assertThat(localArtifacts).containsKey("com.google.cloud.tools:test-project");
    Truth.assertThat(localArtifacts).containsKey("com.google.cloud.tools:test-subproject");
  }

  @Test
  public void testFindLocalArtifacts_absolutePath() {
    Path relativePath = Paths.get("src/test/resources/testproject");
    Path absolutePath = relativePath.toAbsolutePath();
    ImmutableMap<String, String> localArtifactsFromAbsolutePath =
        LinkageMonitor.findLocalArtifacts(system, session, absolutePath);

    ImmutableMap<String, String> localArtifactsFromRelativePath =
        LinkageMonitor.findLocalArtifacts(system, session, relativePath);

    assertEquals(
        "findLocalArtifacts should behave the same for relative and absolute paths",
        localArtifactsFromRelativePath,
        localArtifactsFromAbsolutePath);
  }
}
