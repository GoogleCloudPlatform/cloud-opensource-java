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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/**
 * Directed graph of class references. Given classes in a set of entry points, it provides {@link
 * #isReachable(String)} for a class to check whether the class is reachable from the entry point
 * classes (reachability). The graph's nodes and edges are defined as follows:
 *
 * <p>Nodes are fully-qualified class names, returned from {@link
 * SymbolReference#getSourceClassName()} and {@link SymbolReference#getTargetClassName()} in {@code
 * symbolReferenceSets}.
 *
 * <p>Edges are references between two classes. When {@code ClassA} has a reference to {@code
 * ClassB}, a directed edge from {@code ClassA} to {@code ClassB} exists in the graph. Edges in the
 * graph are anonymous with no attribute. Because self-loops and parallel edges are unnecessary for
 * reachability checks, they are not constructed.
 *
 * @see <a
 *     href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/library-best-practices/glossary.md#class-reference-graph">
 *     Java Dependency Glossary: Class Reference Graph</a>
 */
class ClassReferenceGraph {

  private final ImmutableSet<String> reachableClasses;

  static ClassReferenceGraph create(
      Collection<SymbolReferenceSet> symbolReferenceSets, Set<Path> entryPointJars)
      throws IOException {

    // Collects all class symbol reference in symbolReferences
    ImmutableSet<ClassSymbolReference> classSymbolReferences =
        symbolReferenceSets.stream()
            .flatMap(symbolReferenceSet -> symbolReferenceSet.getClassReferences().stream())
            .collect(toImmutableSet());

    ImmutableSet.Builder<String> entryPointClassBuilder = ImmutableSet.builder();
    for (Path jar : entryPointJars) {
      for (String className : ClassDumper.listClassNamesInJar(jar)) {
        entryPointClassBuilder.add(className);
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

    entryPointClasses.forEach(graph::addNode); // to avoid IllegalArgumentError in breadthFirst

    this.reachableClasses =
        ImmutableSet.copyOf(Traverser.forGraph(graph).breadthFirst(entryPointClasses));
  }

  /**
   * Returns true if {@code className} is reachable from one of classes in {@code entryPointJars}
   * in the graph.
   */
  boolean isReachable(String className) {
    return reachableClasses.contains(className);
  }
}
