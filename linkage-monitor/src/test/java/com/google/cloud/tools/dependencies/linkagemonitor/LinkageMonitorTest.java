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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassSymbol;
import com.google.cloud.tools.opensource.classpath.ErrorType;
import com.google.cloud.tools.opensource.classpath.MethodSymbol;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.junit.Before;
import org.junit.Test;

public class LinkageMonitorTest {
  private RepositorySystem system;
  private RepositorySystem spySystem;
  private RepositorySystemSession session;
  private GenericVersionScheme versionScheme = new GenericVersionScheme();

  @Before
  public void setup() {
    system = RepositoryUtility.newRepositorySystem();

    // If possible, spy object should be avoided. But Maven is tightly coupled with RepositorySystem
    // and thus normal mock objects on RepositorySystem would make the test even complicated.
    // https://static.javadoc.io/org.mockito/mockito-core/3.0.0/org/mockito/Mockito.html#spy-T-
    spySystem = spy(system);
    session = RepositoryUtility.newSession(system);
  }

  private ArtifactResult resolveArtifact(String coordinates) throws ArtifactResolutionException {
    Artifact protobufJavaArtifact = new DefaultArtifact(coordinates);
    return system.resolveArtifact(
        session, new ArtifactRequest(protobufJavaArtifact, ImmutableList.of(CENTRAL), null));
  }

  @Test
  public void testFindSnapshotVersion()
      throws MavenRepositoryException, ModelBuildingException, ArtifactResolutionException {
    // This version of Guava should be found locally because this module uses it.
    Bom snapshotBom =
        LinkageMonitor.copyWithSnapshot(
            system,
            new Bom(
                "com.google.guava:guava-bom:27.1-android",
                ImmutableList.of(new DefaultArtifact("com.google.guava:guava:27.1-android"))));
    assertNotNull(snapshotBom);
  }

  @Test
  public void testFindSnapshotVersionBom()
      throws MavenRepositoryException, ModelBuildingException, ArtifactResolutionException {
    // This version of Guava should be found locally because this module uses it.
    Bom snapshotBom =
        LinkageMonitor.copyWithSnapshot(
            system,
            new Bom(
                "com.google.cloud:libraries-bom:pom:2.2.1",
                ImmutableList.of(new DefaultArtifact("com.google.guava:guava:27.1-android"))));
    assertNotNull(snapshotBom);
  }

  @Test
  public void testBomSnapshot()
      throws VersionRangeResolutionException, MavenRepositoryException,
          InvalidVersionSpecificationException, ModelBuildingException, ArtifactResolutionException,
          ArtifactDescriptorException {
    VersionRangeResult protobufSnapshotVersionResult =
        new VersionRangeResult(new VersionRangeRequest());

    protobufSnapshotVersionResult.setVersions(
        ImmutableList.of(
            versionScheme.parseVersion("3.6.0"),
            versionScheme.parseVersion("3.7.0"),
            versionScheme.parseVersion("3.8.0-SNAPSHOT")));

    // invocation for protobuf-java
    doReturn(protobufSnapshotVersionResult)
        .when(spySystem)
        .resolveVersionRange(
            any(RepositorySystemSession.class),
            argThat(request -> "protobuf-java".equals(request.getArtifact().getArtifactId())));

    ArtifactResult protobufJavaResult = resolveArtifact("com.google.protobuf:protobuf-java:3.8.0");

    Artifact protobufJavaSnapshotArtifact =
        new DefaultArtifact("com.google.protobuf:protobuf-java:3.8.0-SNAPSHOT")
            .setFile(protobufJavaResult.getArtifact().getFile());

    doReturn(protobufJavaResult.setArtifact(protobufJavaSnapshotArtifact))
        .when(spySystem)
        .resolveArtifact(
            any(RepositorySystemSession.class),
            argThat(request -> "protobuf-java".equals(request.getArtifact().getArtifactId())));

    Bom bom = RepositoryUtility.readBom("com.google.cloud:libraries-bom:1.2.0");
    Bom snapshotBom = LinkageMonitor.copyWithSnapshot(spySystem, bom);

    assertEquals(
        "The first element of the SNAPSHOT BOM should be the same as the original BOM",
        "protobuf-java",
        snapshotBom.getManagedDependencies().get(0).getArtifactId());
    assertEquals(
        "The protobuf-java artifact should have SNAPSHOT version",
        "3.8.0-SNAPSHOT",
        snapshotBom.getManagedDependencies().get(0).getVersion());

    int bomSize = bom.getManagedDependencies().size();
    assertEquals(
        "Snapshot BOM should have the same length as original BOM.",
        bomSize,
        snapshotBom.getManagedDependencies().size());
    for (int i = 1; i < bomSize; ++i) {
      Artifact expected = bom.getManagedDependencies().get(i);
      Artifact actual = snapshotBom.getManagedDependencies().get(i);
assertEquals(
          "Artifacts other than protobuf-java should have the original version: "
		  + expected + " != " + actual,
          expected.getVersion(),
          actual.getVersion());
    }
  }

  private final SymbolProblem classNotFoundProblem =
      new SymbolProblem(new ClassSymbol("java.lang.Integer"), ErrorType.CLASS_NOT_FOUND, null);
  private final SymbolProblem methodNotFoundProblem =
      new SymbolProblem(
          new MethodSymbol(
              "io.grpc.protobuf.ProtoUtils.marshaller",
              "marshaller",
              "(Lcom/google/protobuf/Message;)Lio/grpc/MethodDescriptor$Marshaller;",
              false),
          ErrorType.SYMBOL_NOT_FOUND,
          new ClassFile(Paths.get("aaa", "bbb-1.2.3.jar"), "java.lang.Object"));

  @Test
  public void generateMessageForNewError() {
    Set<SymbolProblem> baselineProblems = ImmutableSet.of(classNotFoundProblem);
    ImmutableSetMultimap<SymbolProblem, ClassFile> snapshotProblems =
        ImmutableSetMultimap.of(
            classNotFoundProblem, // This is in baseline. It should not be printed
            new ClassFile(Paths.get("aaa", "bbb-1.2.3.jar"), "com.abc.AAA"),
            methodNotFoundProblem,
            new ClassFile(Paths.get("aaa", "bbb-1.2.3.jar"), "com.abc.AAA"),
            methodNotFoundProblem,
            new ClassFile(Paths.get("aaa", "bbb-1.2.3.jar"), "com.abc.BBB"));

    String message = LinkageMonitor.messageForNewErrors(snapshotProblems, baselineProblems);
    assertEquals(
        "Newly introduced problem:\n"
            + "(bbb-1.2.3.jar) io.grpc.protobuf.ProtoUtils.marshaller's method"
            + " marshaller(com.google.protobuf.Message arg1) is not found\n"
            + "  referenced from com.abc.AAA (bbb-1.2.3.jar)\n"
            + "  referenced from com.abc.BBB (bbb-1.2.3.jar)\n",
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
            + "  (bbb-1.2.3.jar) io.grpc.protobuf.ProtoUtils.marshaller's method "
            + "marshaller(com.google.protobuf.Message arg1) is not found\n",
        message);
  }

  @Test
  public void testBuildModelWithSnapshotBom_noSnapshotUpdate()
      throws MavenRepositoryException, ModelBuildingException, ArtifactResolutionException {
    Model model =
        LinkageMonitor.buildModelWithSnapshotBom(
            system, session, "com.google.cloud:libraries-bom:2.2.1");
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
      throws MavenRepositoryException, ModelBuildingException, ArtifactResolutionException {
    for (String invalidCoordinates : ImmutableList.of("a.b.c:d", "a:b:c:d:e:1", "a::c:0.1")) {
      try {
        LinkageMonitor.buildModelWithSnapshotBom(system, session, invalidCoordinates);
        fail("The method should invalidate coordinates: " + invalidCoordinates);
      } catch (IllegalArgumentException ex) {
        // pass
      }
    }
  }

  @Test
  public void testBuildModelWithSnapshotBom_BomSnapshotUpdate()
      throws MavenRepositoryException, ModelBuildingException, ArtifactResolutionException,
          InvalidVersionSpecificationException, VersionRangeResolutionException {
    // Linkage Monitor should update a BOM in Google Cloud Libraries BOM when it's available local
    // repository. This test case simulates the issue below where
    // google-cloud-bom:0.106.0-alpha-SNAPSHOT should provide gax:1.48.0.
    // https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/853

    VersionRangeResult googleCloudBomVersionRangeResult =
        new VersionRangeResult(new VersionRangeRequest());
    googleCloudBomVersionRangeResult.setVersions(
        ImmutableList.of(
            versionScheme.parseVersion("0.106.0-alpha"),
            versionScheme.parseVersion("0.106.0-alpha-SNAPSHOT")));

    doReturn(googleCloudBomVersionRangeResult)
        .when(spySystem)
        .resolveVersionRange(
            any(RepositorySystemSession.class),
            argThat(request -> "google-cloud-bom".equals(request.getArtifact().getArtifactId())));

    ArtifactResult googleCloudBomResult =
        resolveArtifact("com.google.cloud:google-cloud-bom:pom:0.106.0-alpha");

    doReturn(googleCloudBomResult)
        .when(spySystem)
        .resolveArtifact(
            any(RepositorySystemSession.class),
            argThat(request -> "google-cloud-bom".equals(request.getArtifact().getArtifactId())));

    // Libraries-bom:2.2.1 has google-cloud-bom:0.91.0-alpha, which has gax:1.44.0
    Model model =
        LinkageMonitor.buildModelWithSnapshotBom(
            spySystem, session, "com.google.cloud:libraries-bom:2.2.1");
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
}
