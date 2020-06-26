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
import org.eclipse.aether.graph.DependencyNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses dependency graph and outputs in text format for end user to review.
 */
public class SerializeGraph
{
    private static final String LINE_START_LAST_CHILD = "\\- ";
    private static final String LINE_START_CHILD = "+- ";

    private final Map<DependencyNode, Boolean> visitedNodes = new IdentityHashMap<DependencyNode, Boolean>( 512 );
    private final Set<String> coordinateStrings =  new HashSet<String>();
    private final Map<String, String> coordinateVersionMap = new HashMap<String, String>();
    private StringBuilder builder = new StringBuilder();
    private boolean isRoot = true;

    public String serialize( DependencyNode root )
    {
        // deal with root first
        Artifact rootArtifact = root.getArtifact();
        builder.append( rootArtifact.getGroupId() + ":" + rootArtifact.getArtifactId() + ":" +
                rootArtifact.getExtension() + ":" + rootArtifact.getVersion()).append( System.lineSeparator() );

        for ( int i = 0; i < root.getChildren().size(); i++ )
        {
            if ( i == root.getChildren().size() - 1 )
            {
                builder = dfs( root.getChildren().get( i ),  LINE_START_LAST_CHILD );
            }
            else
            {
                builder = dfs( root.getChildren().get( i ), LINE_START_CHILD );
            }
        }
        return builder.toString();
    }

    private static String getDependencyCoordinate( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();

        if(node.getDependency() == null)
        {
            // should only get here if node is root
            return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" +
                    artifact.getExtension() + ":" + artifact.getVersion();
        }
        String scope = node.getDependency().getScope();
        String coords = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" +
                artifact.getExtension() + ":" + artifact.getVersion();

        if( scope != null && !scope.isEmpty() )
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
        if( coordinateVersionMap.containsKey( getVersionlessCoordinate( node ) ) )
        {
            return coordinateVersionMap.get( getVersionlessCoordinate( node ) );
        }
        return null;
    }

    private String ScopeConflict( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();
        List<String> scopes = Arrays.asList( "compile", "provided", "runtime", "test", "system" );

        for( String scope:scopes )
        {
            String coordinate = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" +
                    artifact.getExtension() + ":" + artifact.getVersion() + ":" + scope;
            if( coordinateStrings.contains( coordinate ) )
            {
                return scope;
            }
        }
        // check for scopeless, this probably can't happen
        return null;
    }

    private StringBuilder dfs( DependencyNode node, String start )
    {
        builder.append( start );
        if( node.getArtifact() == null )
        {
            /* this case happens with tree-verbose test, probably because graphBuilder returns an incomplete graph
             * due to some error with a dependency */
            builder.append( "Null Artifact Node" ).append( System.lineSeparator() );
            // ToDo: move this replicated code to its own method
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
                    builder = dfs( node.getChildren().get( i ), start.concat( LINE_START_LAST_CHILD ) );
                }
                else
                {
                    builder = dfs( node.getChildren().get( i ), start.concat( LINE_START_CHILD ) );
                }
            }
            return builder;
        }
        String coordString = getDependencyCoordinate( node );

        if ( visitedNodes.containsKey( node ) )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for cycle)" )
                    .append( System.lineSeparator() );
        }
        else if ( isDuplicateDependencyCoordinate( node ) )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for duplicate)" )
                    .append( System.lineSeparator() );
        }
        else if ( ScopeConflict( node ) != null )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for conflict with " )
                    .append( ScopeConflict( node ) ).append( ')' ).append( System.lineSeparator() );
        }
        else if ( VersionConflict( node ) != null )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for conflict with " )
                    .append( VersionConflict( node ) ).append( ')' ).append( System.lineSeparator() );
        }
        else if ( node.getDependency() != null && node.getDependency().isOptional() )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted due to optional dependency)" )
                    .append( System.lineSeparator() );
        }
        else
        {
            coordinateStrings.add( getDependencyCoordinate( node ) );
            if( node.getArtifact() != null )
            {
                coordinateVersionMap.put( getVersionlessCoordinate( node ), node.getArtifact().getVersion() );
            }
            builder.append( coordString ).append( System.lineSeparator() );
            visitedNodes.put( node, true );

            // ToDo: move this replicated code to its own method
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
                    builder = dfs( node.getChildren().get( i ), start.concat( LINE_START_LAST_CHILD ) );
                }
                else
                {
                    builder = dfs( node.getChildren().get( i ), start.concat( LINE_START_CHILD ) );
                }
            }
        }
        return builder;
    }
}
