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

import static com.google.cloud.tools.opensource.classpath.TestHelper.classPathEntryOfResource;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.Test;

public class ClassReferenceGraphTest {

  private static final String GRPC_CLOUD_FIRESTORE_JAR =
      "testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar";

  private static ClassReferenceGraph createExampleGraph() throws URISyntaxException, IOException {

    ClassPathEntry firestoreJar = classPathEntryOfResource(GRPC_CLOUD_FIRESTORE_JAR);
    SymbolReferenceMaps.Builder builder = new SymbolReferenceMaps.Builder();
    builder.addClassReference(
        new ClassFile(firestoreJar, "com.google.firestore.v1beta1.FirestoreGrpc"),
        new ClassSymbol("ClassA"));
    builder.addClassReference(new ClassFile(firestoreJar, "ClassA"), new ClassSymbol("ClassB"));
    builder.addClassReference(new ClassFile(firestoreJar, "ClassC"), new ClassSymbol("ClassD"));

    return ClassReferenceGraph.create(
        builder.build(),
        // This jar file contains com.google.firestore.v1beta1.FirestoreGrpc
        ImmutableSet.of(classPathEntryOfResource(GRPC_CLOUD_FIRESTORE_JAR)));
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
