/*
 *  Copyright 2020 Google LLC.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.maven.dependency.graph;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Parses dependency graph and outputs in text format for end user to review.
 */
public class SerializeGraph
{
    private static final String LINE_START_LAST_CHILD = "\\- ";
    private static final String LINE_START_CHILD = "+- ";

    private Map<DependencyNode, String> visitedNodes = new IdentityHashMap<>( 512 );
    private Set<String> coordinateStrings = new HashSet<>();
    private Map<String, String> coordinateVersionMap = new HashMap<>();
    private Map<DependencyNode, String> nodeErrors = new HashMap<>();
    private StringBuilder builder = new StringBuilder();

    public String serialize( DependencyNode root )
    {
        visitedNodes = new IdentityHashMap<>( 512 );
        coordinateStrings = new HashSet<>();
        coordinateVersionMap = new HashMap<>();
        nodeErrors = new HashMap<>();
        builder = new StringBuilder();

        getNodeConflictMessagesBFS( root );


        // Use BFS to mirror how Maven resolves dependencies and use DFS to print the tree easily

        // deal with root first
        Artifact rootArtifact = root.getArtifact();
        builder.append( rootArtifact.getGroupId() + ":" + rootArtifact.getArtifactId() + ":"
                + rootArtifact.getExtension() + ":" + rootArtifact.getVersion() ).append( System.lineSeparator() );

        for ( int i = 0; i < root.getChildren().size(); i++ )
        {
            if ( i == root.getChildren().size() - 1 )
            {
                builder = dfs( root.getChildren().get( i ), LINE_START_LAST_CHILD, true );
            }
            else
            {
                builder = dfs( root.getChildren().get( i ), LINE_START_CHILD, true );
            }
        }
        return builder.toString();
    }

    private static String getDependencyCoordinate( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();

        if ( node.getDependency() == null )
        {
            // should only get here if node is root
            return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":" + artifact.getVersion();
        }
        String scope = node.getDependency().getScope();
        String coords = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":" + artifact.getVersion();

        if ( scope != null && !scope.isEmpty() )
        {
            coords = coords.concat( ":" + scope );
        }
        return coords;
    }

    private static String getVersionlessCoordinate( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();
        // scope not included because we check for scope conflicts separately
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension();
    }

    private boolean isDuplicateDependencyCoordinate( DependencyNode node )
    {
        return coordinateStrings.contains( getDependencyCoordinate( node ) );
    }

    private String VersionConflict( DependencyNode node )
    {
        if ( coordinateVersionMap.containsKey( getVersionlessCoordinate( node ) ) )
        {
            return coordinateVersionMap.get( getVersionlessCoordinate( node ) );
        }
        return null;
    }

    private String ScopeConflict( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();
        List<String> scopes = Arrays.asList( "compile", "provided", "runtime", "test", "system" );

        for ( String scope : scopes )
        {
            String coordinate = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":" + artifact.getVersion() + ":" + scope;
            if ( coordinateStrings.contains( coordinate ) )
            {
                return scope;
            }
        }
        return null;
    }

    private StringBuilder callDfs( DependencyNode node, String start )
    {
        for ( int i = 0; i < node.getChildren().size(); i++ )
        {
            if ( start.endsWith( LINE_START_CHILD ) )
            {
                start = start.replace( LINE_START_CHILD, "|  " );
            }
            else if ( start.endsWith( LINE_START_LAST_CHILD ) )
            {
                start = start.replace( LINE_START_LAST_CHILD, "   " );
            }

            if ( i == node.getChildren().size() - 1 )
            {
                builder = dfs( node.getChildren().get( i ), start.concat( LINE_START_LAST_CHILD ), false );
            }
            else
            {
                builder = dfs( node.getChildren().get( i ), start.concat( LINE_START_CHILD ), false );
            }
        }
        return builder;
    }

    private void getNodeConflictMessagesBFS( DependencyNode root )
    {
        Map<DependencyNode, Boolean> visitedNodes = new IdentityHashMap<>( 512 );
        Queue<DependencyNode> queue = new LinkedList<>();
        visitedNodes.put( root, true );
        queue.add( root );
        boolean isRoot = true;

        while ( !queue.isEmpty() )
        {
            DependencyNode node = queue.poll();

            if ( node == null || node.getArtifact() == null )
            {
                // Should never reach hit this condition with a proper graph sent in
                nodeErrors.put( node, "Null Artifact Node" );
            }

            String coordString = getDependencyCoordinate( node );

            if ( isDuplicateDependencyCoordinate( node ) )
            {
                nodeErrors.put( node, "(" + coordString + " - omitted for duplicate)" + System.lineSeparator() );
            }
            else if ( ScopeConflict( node ) != null )
            {
                nodeErrors.put( node, "(" + coordString + " - omitted for conflict with " + ScopeConflict( node ) + ")"
                    + System.lineSeparator() );
            }
            else if ( VersionConflict( node ) != null )
            {
                nodeErrors.put( node, "(" + coordString + " - omitted for conflict with " + VersionConflict( node )
                    + ")" + System.lineSeparator() );
            }
            else if ( node.getDependency() != null && node.getDependency().isOptional() )
            {
                nodeErrors.put( node, "(" + coordString + " - omitted due to optional dependency)"
                        + System.lineSeparator() );
            }
            else
            {
                boolean ignoreNode = false;
                nodeErrors.put( node, null );
                coordinateStrings.add( getDependencyCoordinate( node ) );
                if ( node.getArtifact() != null )
                {
                    coordinateVersionMap.put( getVersionlessCoordinate( node ), node.getArtifact().getVersion() );
                }

                for ( DependencyNode child : node.getChildren() )
                {
                    if ( visitedNodes.containsKey( child ) )
                    {
                        ignoreNode = true;
                        nodeErrors.put( node, "(" + coordString + " - omitted for introducing a cycle with " +
                                getDependencyCoordinate( child ) + ")" + System.lineSeparator() );
                        node.setChildren( new ArrayList<DependencyNode>() );
                        break;
                    }
                }

                if( !ignoreNode )
                {
                    for ( int i = 0; i < node.getChildren().size(); ++i )
                    {
                        DependencyNode child = node.getChildren().get( i );

                        if ( !visitedNodes.containsKey( child ) )
                        {
                            visitedNodes.put( child, true );
                            queue.add( child );
                        }
                    }
                }
            }
        }
    }

    private StringBuilder bfs( DependencyNode root )
    {
        Queue<DependencyNode> queue = new LinkedList<>();
        visitedNodes.put( root, "" );
        queue.add( root );

        while ( !queue.isEmpty() )
        {
            DependencyNode node = queue.poll();
            builder.append( visitedNodes.get( node ) );

            if ( node == null || node.getArtifact() == null )
            {
                // Should never reach hit this condition with a proper graph sent in
                builder.append( "Null Artifact Node" ).append( System.lineSeparator() );
                return builder;
            }

            String coordString = getDependencyCoordinate( node );

            /*if ( visitedNodes.containsKey( node ) )
            {
                builder.append( '(' ).append( coordString ).append( " - omitted for cycle)" ).append(
                        System.lineSeparator() );
            }
            else*/
            if ( isDuplicateDependencyCoordinate( node ) )
            {
                builder.append( '(' ).append( coordString ).append( " - omitted for duplicate)" ).append(
                        System.lineSeparator() );
            }
            else if ( ScopeConflict( node ) != null )
            {
                builder.append( '(' ).append( coordString ).append( " - omitted for conflict with " ).append(
                        ScopeConflict( node ) ).append( ')' ).append( System.lineSeparator() );
            }
            else if ( VersionConflict( node ) != null )
            {
                builder.append( '(' ).append( coordString ).append( " - omitted for conflict with " ).append(
                        VersionConflict( node ) ).append( ')' ).append( System.lineSeparator() );
            }
            else if ( node.getDependency() != null && node.getDependency().isOptional() )
            {
                builder.append( '(' ).append( coordString ).append( " - omitted due to optional dependency)" ).append(
                        System.lineSeparator() );
            }
            else
            {
                coordinateStrings.add( getDependencyCoordinate( node ) );
                if ( node.getArtifact() != null )
                {
                    coordinateVersionMap.put( getVersionlessCoordinate( node ), node.getArtifact().getVersion() );
                }
                builder.append( coordString ).append( System.lineSeparator() );

                for ( int i = 0; i < node.getChildren().size(); ++i )
                {
                    DependencyNode child = node.getChildren().get( i );

                    if ( !visitedNodes.containsKey( child ) )
                    {
                        String parentStart = visitedNodes.get( node );
                        String start;

                        if ( parentStart.endsWith( "+- " ) )
                        {
                            start = parentStart.replace( "+- ", "   |  " );
                        }
                        else
                        {
                            start = parentStart.replace( "\\-", "   " );
                        }

                        if ( i != node.getChildren().size() - 1 )
                        {
                            start = start.concat( "+- " );
                        }
                        else
                        {
                            start = start.concat( "\\- " );
                        }
                        visitedNodes.put( child, start );
                        queue.add( child );
                    }
                }
            }
        }
        return builder;
    }

    private StringBuilder dfs( DependencyNode node, String start, boolean firstLevel )
    {
        builder.append( start );
        if ( node.getArtifact() == null )
        {
            // Should never reach hit this condition with a proper graph sent in
            builder.append( "Null Artifact Node" ).append( System.lineSeparator() );
            callDfs( node, start );
        }

        String coordString = getDependencyCoordinate( node );

        if ( node.getDependency().getScope().equals( "test" ) && !firstLevel )
        {
            // don't want transitive test dependencies included
            return builder;
        }
        else if ( nodeErrors.get( node ) != null )
        {
            builder.append( nodeErrors.get( node ) );
        }
        /*else if ( visitedNodes.containsKey( node ) )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for cycle)" ).append(
                    System.lineSeparator() );
        }
        else if ( isDuplicateDependencyCoordinate( node ) )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for duplicate)" ).append(
                    System.lineSeparator() );
        }
        else if ( ScopeConflict( node ) != null )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for conflict with " ).append(
                    ScopeConflict( node ) ).append( ')' ).append( System.lineSeparator() );
        }
        else if ( VersionConflict( node ) != null )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for conflict with " ).append(
                    VersionConflict( node ) ).append( ')' ).append( System.lineSeparator() );
        }
        else if ( node.getDependency() != null && node.getDependency().isOptional() )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted due to optional dependency)" ).append(
                    System.lineSeparator() );
        }*/
        else
        {
            // coordinateStrings.add( getDependencyCoordinate( node ) );
            /*if ( node.getArtifact() != null )
            {
                coordinateVersionMap.put( getVersionlessCoordinate( node ), node.getArtifact().getVersion() );
            }*/
            builder.append( coordString ).append( System.lineSeparator() );
            // visitedNodes.put( node, "" );
            callDfs( node, start );
        }
        return builder;
    }
}
