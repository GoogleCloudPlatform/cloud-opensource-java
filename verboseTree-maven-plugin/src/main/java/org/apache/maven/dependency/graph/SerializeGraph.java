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
 * Class to parse dependency graph and output in text format for end user to review
 *
 */
public class SerializeGraph
{
    // will be injected eventually
    private String outputType;

    private final String lastChildStart = "\\- ";
    private final String notLastChildStart = "+- ";

    private final Map<DependencyNode, Boolean> visitedNodes;
    private final Set<String> coordinateStrings;
    private final Map<String, String> coordinateVersionMap;
    private StringBuilder builder;

    public SerializeGraph()
    {
        visitedNodes = new IdentityHashMap<DependencyNode, Boolean>( 512 );
        coordinateStrings = new HashSet<String>();
        coordinateVersionMap = new HashMap<String, String>();
        builder = new StringBuilder();
    }

    public String serialize( DependencyNode root )
    {
        return dfs( root, "" ).toString();
    }

    private static String getCoordinateString( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();
        String scope = node.getDependency().getScope();

        if( scope != null && !scope.isEmpty() )
        {
            return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" +
                    artifact.getExtension() + ":" + artifact.getVersion() + ":" + scope;
        }
        // the scope should theoretically not be null or empty
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" +
                artifact.getExtension() + ":" + artifact.getVersion();
    }

    private static String getVersionlessCoordinateString( DependencyNode node )
    {
        Artifact artifact = node.getArtifact();

        // scope not included because we check for scope conflicts separately
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getExtension();
    }

    private boolean isDuplicateCoordinateString( DependencyNode node )
    {
        return coordinateStrings.contains( getCoordinateString( node ) );
    }

    private String VersionConflict( DependencyNode node )
    {
        if( coordinateVersionMap.containsKey( getVersionlessCoordinateString( node ) ) )
        {
            return coordinateVersionMap.get( getVersionlessCoordinateString( node ) );
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
            if(coordinateStrings.contains( coordinate ))
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
        String coordString = getCoordinateString( node );

        if ( visitedNodes.containsKey( node ) )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted for cycle)" )
                    .append( System.lineSeparator() );
        }
        else if ( isDuplicateCoordinateString( node ) )
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
        else if ( node.getDependency().isOptional() )
        {
            builder.append( '(' ).append( coordString ).append( " - omitted due to optional dependency)" )
                    .append( System.lineSeparator() );
        }
        else
        {
            coordinateStrings.add( getCoordinateString( node ) );
            coordinateVersionMap.put( getVersionlessCoordinateString( node ), node.getArtifact().getVersion() );
            builder.append( coordString ).append( System.lineSeparator() );
            visitedNodes.put( node, true );

            for ( int i = 0; i < node.getChildren().size(); i++ )
            {
                if ( start.endsWith( notLastChildStart ) )
                {
                    start = start.replace( notLastChildStart, "|  " );
                }
                else if ( start.endsWith( lastChildStart ) )
                {
                    start = start.replace( lastChildStart, "   " );
                }

                if ( i == node.getChildren().size() - 1 )
                {
                    builder = dfs( node.getChildren().get( i ), start.concat( lastChildStart ) );
                }
                else
                {
                    builder = dfs( node.getChildren().get( i ), start.concat( notLastChildStart ) );
                }
            }
        }
        return builder;
    }
}
