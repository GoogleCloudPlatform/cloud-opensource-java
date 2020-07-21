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

import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.Artifact;
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
public final class SerializeGraph
{
    private static final String LINE_START_LAST_CHILD = "\\- ";
    private static final String LINE_START_CHILD = "+- ";

    public String serialize( DependencyNode root )
    {
        Set<String> coordinateStrings = new HashSet<>();
        Map<String, String> coordinateVersionMap = new HashMap<>();
        StringBuilder builder = new StringBuilder();

        // Use BFS to mirror how Maven resolves dependencies and use DFS to print the tree easily
        Map<DependencyNode, String> nodeErrors = getNodeConflictMessagesBFS( root, coordinateStrings,
                coordinateVersionMap );

        // deal with root first
        Artifact rootArtifact = root.getArtifact();
        builder.append( rootArtifact.getGroupId() ).append( ":" ).append( rootArtifact.getArtifactId() ).append( ":" )
                .append( rootArtifact.getExtension() ).append( ":" ).append( rootArtifact.getVersion() ).append(
                        System.lineSeparator() );

        for ( int i = 0; i < root.getChildren().size(); i++ )
        {
            if ( i == root.getChildren().size() - 1 )
            {
                builder = dfsPrint( root.getChildren().get( i ), LINE_START_LAST_CHILD, true, builder,
                        nodeErrors );
            }
            else
            {
                builder = dfsPrint( root.getChildren().get( i ), LINE_START_CHILD, true, builder, nodeErrors );
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
            return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":"
                    + artifact.getVersion();
        }
        String scope = node.getDependency().getScope();
        String coords = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension() + ":"
                + artifact.getVersion();

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

    private static boolean isDuplicateDependencyCoordinate( DependencyNode node, Set<String> coordinateStrings )
    {
        return coordinateStrings.contains( getDependencyCoordinate( node ) );
    }

    private static String VersionConflict( DependencyNode node, Map<String, String> coordinateVersionMap )
    {
        if ( coordinateVersionMap.containsKey( getVersionlessCoordinate( node ) ) )
        {
            return coordinateVersionMap.get( getVersionlessCoordinate( node ) );
        }
        return null;
    }

    private static String ScopeConflict( DependencyNode node, Set<String> coordinateStrings )
    {
        Artifact artifact = node.getArtifact();
        List<String> scopes = Arrays.asList( "compile", "provided", "runtime", "test", "system" );

        for ( String scope : scopes )
        {
            String coordinate = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension()
                    + ":" + artifact.getVersion() + ":" + scope;
            if ( coordinateStrings.contains( coordinate ) )
            {
                return scope;
            }
        }
        return null;
    }

    private StringBuilder callDfsPrint( DependencyNode node, String start, StringBuilder builder,
                                        Map<DependencyNode, String> nodeErrors )
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
                builder = dfsPrint( node.getChildren().get( i ), start.concat( LINE_START_LAST_CHILD ), false,
                        builder, nodeErrors);
            }
            else
            {
                builder = dfsPrint( node.getChildren().get( i ), start.concat( LINE_START_CHILD ), false,
                        builder, nodeErrors );
            }
        }
        return builder;
    }

    private Map<DependencyNode, String> getNodeConflictMessagesBFS( DependencyNode root, Set<String> coordinateStrings,
                                             Map<String, String> coordinateVersionMap )
    {
        Map<DependencyNode, String> nodeErrors = new HashMap<>();
        Set<DependencyNode> visitedNodes = new HashSet<>( 512 );
        Queue<DependencyNode> queue = new LinkedList<>();
        visitedNodes.add( root );
        queue.add( root );

        while ( !queue.isEmpty() )
        {
            DependencyNode node = queue.poll();

            if ( node == null || node.getArtifact() == null )
            {
                // Should never reach hit this condition with a proper graph sent in
                nodeErrors.put( node, "Null Artifact Node" );
                break;
            }

            String coordString = getDependencyCoordinate( node );

            if ( isDuplicateDependencyCoordinate( node, coordinateStrings ) )
            {
                nodeErrors.put( node, "(" + coordString + " - omitted for duplicate)" + System.lineSeparator() );
            }
            else if ( ScopeConflict( node, coordinateStrings ) != null )
            {
                nodeErrors.put( node, "(" + coordString + " - omitted for conflict with " +
                        ScopeConflict( node, coordinateStrings ) + ")" + System.lineSeparator() );
            }
            else if ( VersionConflict( node, coordinateVersionMap ) != null )
            {
                nodeErrors.put( node, "(" + coordString + " - omitted for conflict with " +
                        VersionConflict( node, coordinateVersionMap ) + ")" + System.lineSeparator() );
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
                    if ( visitedNodes.contains( child ) )
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

                        if ( !visitedNodes.contains( child ) )
                        {
                            visitedNodes.add( child );
                            queue.add( child );
                        }
                    }
                }
            }
        }
        return nodeErrors;
    }

    private StringBuilder dfsPrint( DependencyNode node, String start, boolean firstLevel, StringBuilder builder,
                                    Map<DependencyNode, String> nodeErrors )
    {
        builder.append( start );
        if ( node.getArtifact() == null )
        {
            // Should never reach hit this condition with a proper graph sent in
            builder.append( "Null Artifact Node" ).append( System.lineSeparator() );
            callDfsPrint( node, start, builder, nodeErrors );
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
        else
        {
            builder.append( coordString ).append( System.lineSeparator() );
            callDfsPrint( node, start, builder, nodeErrors );
        }
        return builder;
    }
}
