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

package com.google.cloud.tools.opensource.dependencies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that records all paths leading to nodes matching a certain filter criteria.
 * This visits only unique nodes.
 */
public final class UniquePathRecordingDependencyVisitor
    implements DependencyVisitor
{

  private final DependencyFilter filter;

  private final List<List<DependencyNode>> paths;

  private final List<DependencyNode> parents;

  private final IdentityHashMap<DependencyNode, Object> visited;

  /**
   * Creates a new visitor that uses the specified filter to identify terminal nodes of interesting paths. The visitor
   * will not search for paths going beyond an already matched node.
   *
   * @param filter The filter used to select terminal nodes of paths to record, may be {@code null} to match any node.
   */

  /**
   * Creates a new visitor that uses the specified filter to identify terminal nodes of interesting paths.
   *
   * @param filter The filter used to select terminal nodes of paths to record, may be {@code null} to match any node.
   */
  public UniquePathRecordingDependencyVisitor( DependencyFilter filter )
  {
    this.filter = filter;
    paths = new ArrayList<>();
    parents = new ArrayList<>();
    visited = new IdentityHashMap<>( 128 );
  }

  /**
   * Gets the filter being used to select terminal nodes.
   *
   * @return The filter being used or {@code null} if none.
   */
  public DependencyFilter getFilter()
  {
    return filter;
  }

  /**
   * Gets the paths leading to nodes matching the filter that have been recorded during the graph visit. A path is
   * given as a sequence of nodes, starting with the root node of the graph and ending with a node that matched the
   * filter.
   *
   * @return The recorded paths, never {@code null}.
   */
  public List<List<DependencyNode>> getPaths()
  {
    return paths;
  }

  public boolean visitEnter( DependencyNode node )
  {
    parents.add( node );

    if ( filter.accept( node, parents ) )
    {
      paths.add( new ArrayList<>(parents) );

      visited.put( node, Boolean.TRUE );
      return false;
    }

    if ( visited.put( node, Boolean.TRUE ) != null )
    {
      return false;
    }

    return true;
  }

  public boolean visitLeave( DependencyNode node )
  {
    parents.remove(parents.size() -1);
    return true;
  }

}
