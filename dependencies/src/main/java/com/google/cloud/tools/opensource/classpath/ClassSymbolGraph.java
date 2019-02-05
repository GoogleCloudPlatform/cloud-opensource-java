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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.SuccessorsFunction;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.apache.bcel.classfile.JavaClass;

/**
 * Directed graph among class references to check a class is reachable from classes in {@code
 * entryPointJars}.
 *
 * <p>Nodes are class names.
 *
 * <p>Edges are directed references between two classes.
 */
class ClassSymbolGraph {
  private final ImmutableSet<String> reachableClasses;

  static ClassSymbolGraph create(Set<ClassSymbolReference> classSymbolReferences,
      Set<Path> entryPointJars) throws IOException {
    ImmutableSet.Builder<String> entryPointClassBuilder = ImmutableSet.builder();
    for (Path jarPath : entryPointJars) {
      for (JavaClass javaClass : ClassDumper.listClassesInJar(jarPath)) {
        entryPointClassBuilder.add(javaClass.getClassName());
      }
    }

    return new ClassSymbolGraph(classSymbolReferences, entryPointClassBuilder.build());
  }

  private ClassSymbolGraph(Set<ClassSymbolReference> classSymbolReferences,
      Set<String> entryPointClasses) {
    MutableGraph<String> graphBuilder = GraphBuilder.directed().allowsSelfLoops(false).build();

    for (ClassSymbolReference reference : classSymbolReferences) {
      String sourceClassName = reference.getSourceClassName();
      String targetClassName = reference.getTargetClassName();
      if (sourceClassName.equals(targetClassName)) {
        continue;
      }
      graphBuilder.putEdge(sourceClassName, targetClassName);
    }

    ImmutableGraph<String> graph = ImmutableGraph.copyOf(graphBuilder);

    ImmutableSet.Builder<String> reachableClassBuilder = ImmutableSet.builder();
    for (String entryPointClass : entryPointClasses) {
      if (graph.nodes().contains(entryPointClass)) {
        reachableClassBuilder.addAll(Graphs.reachableNodes(graph, entryPointClass));
      }
    }
    this.reachableClasses = reachableClassBuilder.build();
  }

  /**
   * Returns true if {@code className} is reachable from one of classes in {@code entryPointJars}.
   */
  private boolean isReachable(String className) {
    return reachableClasses.contains(className);
  }

  /**
   * Returns true if {@code linkageError}'s source class is true for {@link #isReachable(String)}.
   */
  <R extends SymbolReference> boolean isReachableError(StaticLinkageError<R> linkageError) {
    String sourceClassName = linkageError.getReference().getSourceClassName();
    return isReachable(sourceClassName);
  }
}
