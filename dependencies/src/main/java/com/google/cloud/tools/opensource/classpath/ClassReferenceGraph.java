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
import com.google.common.graph.MutableGraph;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import org.apache.bcel.classfile.JavaClass;

/**
 * Directed graph of class references. Given classes in {@code entryPointJars}, it provides
 * {@link #isReachable(String)} for a class to check the reachability. The graph's node and edges
 * are defined as following:
 *
 * <p>Nodes are class names.
 *
 * <p>Edges are references between two classes. When {@code ClassA} has a reference to {@code
 * ClassB}, a directed edge from {@code ClassA} to {@code ClassB} exists in the graph. Because
 * self-loops and parallel edges are unnecessary for reachability checks, they are not allowed.
 *
 * @see <a href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#class-reference-graph">
 *   Java Dependency Glossary: Class Reference Graph</a>
 */
class ClassReferenceGraph {

  private final ImmutableSet<String> reachableClasses;

  static ClassReferenceGraph create(Set<ClassSymbolReference> classSymbolReferences,
      Set<Path> entryPointJars) throws IOException {
    ImmutableSet.Builder<String> entryPointClassBuilder = ImmutableSet.builder();
    for (Path jarPath : entryPointJars) {
      for (JavaClass javaClass : ClassDumper.listClassesInJar(jarPath)) {
        entryPointClassBuilder.add(javaClass.getClassName());
      }
    }

    return new ClassReferenceGraph(classSymbolReferences, entryPointClassBuilder.build());
  }

  private ClassReferenceGraph(Set<ClassSymbolReference> classSymbolReferences,
      Set<String> entryPointClasses) {
    MutableGraph<String> graph = GraphBuilder.directed().allowsSelfLoops(false).build();

    for (ClassSymbolReference reference : classSymbolReferences) {
      String sourceClassName = reference.getSourceClassName();
      String targetClassName = reference.getTargetClassName();
      if (sourceClassName.equals(targetClassName)) {
        continue; // no self-loop
      }
      graph.putEdge(sourceClassName, targetClassName);
    }

    this.reachableClasses = reachableNodes(graph, entryPointClasses);
  }

  /**
   * Returns a set of class names reachable from {@code fromNodes} by following edges in the graph.
   */
  private static ImmutableSet<String> reachableNodes(Graph<String> graph, Set<String> fromNodes) {
    // This function is mostly copy from Graphs.reachableNodes(Graph<N>, N node), except that this
    // function handles multiple fromNodes in the arguments
    Set<String> visitedNodes = Sets.newHashSet();
    visitedNodes.addAll(fromNodes);
    Queue<String> queuedNodes = new ArrayDeque<>(fromNodes);
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
   * Returns true if {@code className} is reachable from one of classes in {@code entryPointJars}
   * in the graph.
   */
  boolean isReachable(String className) {
    return reachableClasses.contains(className);
  }
}
