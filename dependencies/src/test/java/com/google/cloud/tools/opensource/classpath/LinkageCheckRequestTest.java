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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Paths;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.junit.Test;

public class LinkageCheckRequestTest {

  @Test
  public void testCreation() throws IOException {
    LinkageCheckRequest.Builder builder =
        LinkageCheckRequest.builder(ImmutableList.of(new ClassPathEntry(Paths.get("dummy.jar"))));
    LinkageCheckRequest request = builder.build();

    ImmutableList<ClassPathEntry> classPath = request.getClassPath();
    Truth.assertThat(classPath).hasSize(1);
    assertEquals(Paths.get("dummy.jar"), classPath.get(0).getJar());
    assertFalse(request.reportOnlyReachable());
  }

  @Test
  public void testCreation_reportOnlyReachable() throws IOException {
    LinkageCheckRequest.Builder builder =
        LinkageCheckRequest.builder(ImmutableList.of(new ClassPathEntry(Paths.get("dummy.jar"))));
    builder.reportOnlyReachable(ImmutableSet.of(new ClassPathEntry(Paths.get("dummy.jar"))));
    LinkageCheckRequest request = builder.build();

    ImmutableSet<ClassPathEntry> entryPoints = request.getEntryPoints();
    Truth.assertThat(entryPoints).hasSize(1);
    assertEquals(Paths.get("dummy.jar"), entryPoints.iterator().next().getJar());
    assertTrue(request.reportOnlyReachable());
  }

  @Test
  public void testCreation_bom() throws IOException, ArtifactDescriptorException {
    Bom bom = RepositoryUtility.readBom("com.google.cloud:libraries-bom:1.0.0");
    LinkageCheckRequest.Builder builder = LinkageCheckRequest.builder(bom);

    LinkageCheckRequest request = builder.build();

    // This is the class path to find linkage errors in. This includes the dependencies of BOM
    // members.
    ImmutableList<ClassPathEntry> classPath = request.getClassPath();
    Truth.assertThat(classPath).hasSize(271);

    // They are the Maven artifacts in the BOM
    ImmutableSet<ClassPathEntry> entryPoints = request.getEntryPoints();
    Truth.assertThat(entryPoints).hasSize(187);

    assertFalse(request.reportOnlyReachable());
  }
}
