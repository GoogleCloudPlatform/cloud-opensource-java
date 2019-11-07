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
import com.google.cloud.tools.opensource.classpath.ClassSymbol;
import com.google.cloud.tools.opensource.classpath.ErrorType;
import com.google.cloud.tools.opensource.classpath.MethodSymbol;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Truth;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.Before;
import org.junit.Test;

public class LinkageMonitorTest {
  private RepositorySystem system;
  private RepositorySystemSession session;

  @Before
  public void setup() {
    system = RepositoryUtility.newRepositorySystem();

    session = RepositoryUtility.newSession(system);
  }

  @Test
  public void testBomSnapshot()
      throws ModelBuildingException, ArtifactResolutionException, ArtifactDescriptorException {

    Bom bom = RepositoryUtility.readBom("com.google.cloud:libraries-bom:1.2.0");
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

  private final SymbolProblem classNotFoundProblem =
      new SymbolProblem(new ClassSymbol("java.lang.Integer"), ErrorType.CLASS_NOT_FOUND, null);
  private final SymbolProblem methodNotFoundProblem =
      new SymbolProblem(
          new MethodSymbol(
              "io.grpc.protobuf.ProtoUtils",
              "marshaller",
              "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
              false),
          ErrorType.SYMBOL_NOT_FOUND,
          new ClassFile(Paths.get("aaa", "bbb-1.2.3.jar"), "java.lang.Object"));

  @Test
  public void generateMessageForNewError() {
    Set<SymbolProblem> baselineProblems = ImmutableSet.of(classNotFoundProblem);
    Path jar = Paths.get("aaa", "ccc-1.2.3.jar");
    ImmutableSetMultimap<SymbolProblem, ClassFile> snapshotProblems =
        ImmutableSetMultimap.of(
            classNotFoundProblem, // This is in baseline. It should not be printed
            new ClassFile(jar, "com.abc.AAA"),
            methodNotFoundProblem,
            new ClassFile(jar, "com.abc.AAA"),
            methodNotFoundProblem,
            new ClassFile(jar, "com.abc.BBB"));

    DependencyPath dependencyPath = new DependencyPath();
    dependencyPath.add(new DefaultArtifact("foo:bar:1.0.0"), "provided", false);
    dependencyPath.add(new DefaultArtifact("aaa:ccc:1.2.3"), "compile", true);
    String message =
        LinkageMonitor.messageForNewErrors(
            snapshotProblems, baselineProblems, ImmutableListMultimap.of(jar, dependencyPath));
    assertEquals(
        "Newly introduced problem:\n"
            + "(bbb-1.2.3.jar) io.grpc.protobuf.ProtoUtils's method"
            + " marshaller(com.google.protobuf.Message arg1) is not found\n"
            + "  referenced from com.abc.AAA (ccc-1.2.3.jar)\n"
            + "  referenced from com.abc.BBB (ccc-1.2.3.jar)\n"
            + "ccc-1.2.3.jar is at:\n"
            + "  foo:bar:1.0.0 (provided) / aaa:ccc:1.2.3 (compile, optional)\n",
        message);
  }

  @Test
  public void testGenerateMessageForFixedError() {
    String message =
        LinkageMonitor.messageForFixedErrors(
            ImmutableSet.of(classNotFoundProblem, methodNotFoundProblem));
    assertEquals(
        "The following problems in the baseline no longer appear in the snapshot:\n"
            + "  Class java.lang.Integer is not found\n"
            + "  (bbb-1.2.3.jar) io.grpc.protobuf.ProtoUtils's method "
            + "marshaller(com.google.protobuf.Message arg1) is not found\n",
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

    // Google-cloud-bom:0.106.0 has new artifacts such as google-cloud-gameservices
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
  public void testFindLocalArtifacts_currentDirectory() {
    // Current working directory of linkage-monitor should have one linkage monitor artifact
    ImmutableMap<String, String> localArtifacts =
        LinkageMonitor.findLocalArtifacts(system, session, Paths.get("."));

    Truth.assertThat(localArtifacts).hasSize(1);
    Truth.assertThat(localArtifacts).containsKey("com.google.cloud.tools:linkage-monitor");
  }

  @Test
  public void testFindLocalArtifacts_RootDirectory() {
    // Root of cloud-opensource-java has more than 10 pom.xml files
    ImmutableMap<String, String> localArtifacts =
        LinkageMonitor.findLocalArtifacts(system, session, Paths.get(".."));
    Truth.assertThat(localArtifacts.size()).isGreaterThan(10);

    Truth.assertThat(localArtifacts).containsKey("com.google.cloud.tools:linkage-monitor");
    Truth.assertThat(localArtifacts)
        .containsAtLeast(
            "com.google.cloud.tools.opensource:no-such-method-error-example", "1.0-SNAPSHOT");
  }
}
