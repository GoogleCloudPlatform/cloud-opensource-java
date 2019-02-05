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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
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
    reachableClassBuilder.addAll(reachableNodes(graph, entryPointClasses));
    this.reachableClasses = reachableClassBuilder.build();
  }

  private static ImmutableSet<String> reachableNodes(Graph<String> graph, Set<String> nodes) {
    // This function is mostly copy from Graphs.reachableNodes(Graph<N>, N node)
    Set<String> visitedNodes = Sets.newHashSet();
    Queue<String> queuedNodes = new ArrayDeque<>();
    visitedNodes.addAll(nodes);
    queuedNodes.addAll(nodes);
    while (!queuedNodes.isEmpty()) {
      String currentNode = queuedNodes.remove();
      if (!graph.nodes().contains(currentNode)) {
        continue;
      }
      for (String successor : graph.successors(currentNode)) {
        if (visitedNodes.add(successor)) {
          queuedNodes.add(successor);
        }
      }
    }
    return ImmutableSet.copyOf(visitedNodes);
  }

  /**
   * Returns true if {@code className} is reachable from one of classes in {@code entryPointJars}.
   * This method does not perform graph traversal as {@link #reachableClasses} is cached.
   */
  boolean isReachable(String className) {
    return reachableClasses.contains(className);
  }
}
