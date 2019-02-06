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

package com.google.cloud.tools.opensource.classpath;

import static com.google.cloud.tools.opensource.classpath.ClassDumperTest.EXAMPLE_JAR_FILE;
import static com.google.cloud.tools.opensource.classpath.ClassDumperTest.absolutePathOfResource;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.Test;

public class ClassReferenceGraphTest {
  private static ClassReferenceGraph createExampleGraph() throws URISyntaxException, IOException {
    ClassSymbolReference grpcToA =
        ClassSymbolReference.builder()
            .setSourceClassName("com.google.firestore.v1beta1.FirestoreGrpc")
            .setSubclass(false)
            .setTargetClassName("ClassA")
            .build();
    ClassSymbolReference aToB =
        ClassSymbolReference.builder()
            .setSourceClassName("ClassA")
            .setSubclass(false)
            .setTargetClassName("ClassB")
            .build();
    ClassSymbolReference cToD =
        ClassSymbolReference.builder()
            .setSourceClassName("ClassC")
            .setSubclass(false)
            .setTargetClassName("ClassD")
            .build();

    SymbolReferenceSet symbolReferenceSet =
        SymbolReferenceSet.builder()
            .setClassReferences(ImmutableSet.of(grpcToA, aToB, cToD))
            .setFieldReferences(ImmutableSet.of())
            .setMethodReferences(ImmutableSet.of())
            .build();
    return ClassReferenceGraph.create(
        ImmutableSet.of(symbolReferenceSet),
        // This jar file contains com.google.firestore.v1beta1.FirestoreGrpc
        ImmutableSet.of(absolutePathOfResource(EXAMPLE_JAR_FILE)));
  }

  @Test
  public void testClassReachableClass() throws URISyntaxException, IOException {
    ClassReferenceGraph classReferenceGraph = createExampleGraph();

    // Given FirestoreGrpc-to-ClassA and ClassA-to-ClassB, class B is reachable.
    Truth.assertThat(classReferenceGraph.isReachable("ClassB")).isTrue();
  }

  @Test
  public void testUnreachableClass() throws URISyntaxException, IOException {
    ClassReferenceGraph classReferenceGraph = createExampleGraph();

    // There is no path from Firestore Grpc classes to ClassC.
    Truth.assertThat(classReferenceGraph.isReachable("ClassC")).isFalse();
  }
}
