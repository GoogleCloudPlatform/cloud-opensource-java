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

package com.google.cloud.tools.opensource.dependencies;

import com.google.cloud.tools.opensource.classpath.ClassSymbol;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import com.google.common.truth.Truth;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

public class ExceptionAndPathTest {

  private static final RepositoryException repositoryException = new RepositoryException("Dummy Exception");

  static ExceptionAndPath createDummyInstance() {
    Artifact jamonApiArtifact = new DefaultArtifact("com.jamonapi:jamon:2.81");
    Artifact springContextArtifact =
        new DefaultArtifact("org.springframework:spring-context:4.0.2.RELEASE");
    DependencyNode jamonDependencyNode = new DefaultDependencyNode(jamonApiArtifact);
    DependencyNode springContextDependencyNode = new DefaultDependencyNode(springContextArtifact);

    ExceptionAndPath exceptionAndPath =
        ExceptionAndPath.create(
            ImmutableList.of(jamonDependencyNode),
            springContextDependencyNode,
            repositoryException);
    return exceptionAndPath;
  }

  @Test
  public void testCreation() {
    ExceptionAndPath exceptionAndPath = createDummyInstance();

    Truth.assertThat(exceptionAndPath.getPath()).hasSize(2);
    Truth.assertThat(exceptionAndPath.getException().getMessage()).isEqualTo("Dummy Exception");
  }

  @Test
  public void testNull() {
    new NullPointerTester().testConstructors(ExceptionAndPath.class, Visibility.PACKAGE);
  }
}
