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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.Set;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.junit.Before;
import org.junit.Test;

public class LinkageMonitorTest {
  private Bom bom;

  @Before
  public void setup() throws ArtifactDescriptorException {
    bom = RepositoryUtility.readBom("com.google.cloud:libraries-bom:1.2.0");
  }

  @Test
  public void testFindSnapshotVersion() throws MavenRepositoryException {
    // This version of Guava should be found locally because this module uses it.
    Bom snapshotBom =
        LinkageMonitor.copyWithSnapshot(
            RepositoryUtility.newRepositorySystem(),
            new Bom(
                "com.google.cloud.tools:test-bom:0.0.1",
                ImmutableList.of(new DefaultArtifact("com.google.guava:guava:27.1-android"))));
    assertNotNull(snapshotBom);
  }

  @Test
  public void testBomSnapshot()
      throws VersionRangeResolutionException, MavenRepositoryException,
          InvalidVersionSpecificationException {
    VersionRangeResult protobufSnapshotVersionResult =
        new VersionRangeResult(new VersionRangeRequest());
    VersionRangeResult versionWithoutSnapshot = new VersionRangeResult(new VersionRangeRequest());
    GenericVersionScheme versionScheme = new GenericVersionScheme();
    protobufSnapshotVersionResult.setVersions(
        ImmutableList.of(
            versionScheme.parseVersion("3.6.0"),
            versionScheme.parseVersion("3.7.0"),
            versionScheme.parseVersion("3.8.0-SNAPSHOT")));
    versionWithoutSnapshot.setVersions(
        ImmutableList.of(versionScheme.parseVersion("1.2.3"), versionScheme.parseVersion("1.1.1")));

    RepositorySystem mockSystem = mock(RepositorySystem.class);
    when(mockSystem.resolveVersionRange(
            any(RepositorySystemSession.class), any(VersionRangeRequest.class)))
        .thenReturn(versionWithoutSnapshot); // other invocations than protobuf-java
    when(mockSystem.resolveVersionRange(
            any(RepositorySystemSession.class),
            argThat(request -> "protobuf-java".equals(request.getArtifact().getArtifactId()))))
        .thenReturn(protobufSnapshotVersionResult); // invocation for protobuf-java

    Bom snapshotBom = LinkageMonitor.copyWithSnapshot(mockSystem, bom);

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
      assertEquals(
          "Artifacts other than protobuf-java should have the original version",
          bom.getManagedDependencies().get(i).getVersion(),
          snapshotBom.getManagedDependencies().get(i).getVersion());
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
}
