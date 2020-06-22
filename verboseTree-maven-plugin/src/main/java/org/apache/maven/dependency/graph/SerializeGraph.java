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

    private final Map<DependencyNode, Boolean> visitedNodes;
    private final Set<String> versionlessCoordinateStrings;
    private final Set<String> coordinateStrings;
    private StringBuilder builder;

    public SerializeGraph()
    {
        visitedNodes = new IdentityHashMap<DependencyNode, Boolean>( 512 );
        versionlessCoordinateStrings = new HashSet<String>();
        coordinateStrings = new HashSet<String>();
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

    private boolean isVersionConflict( DependencyNode node )
    {
        return versionlessCoordinateStrings.contains( getVersionlessCoordinateString( node ) );
    }

    private boolean isScopeConflict( DependencyNode node )
    {
        String nodeScope = node.getDependency().getScope();
        Artifact artifact = node.getArtifact();
        List<String> scopes = Arrays.asList( "compile", "provided", "runtime", "test", "system" );

        for( String scope:scopes )
        {
            String coordinate = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" +
                    artifact.getExtension() + ":" + artifact.getVersion() + ":" + scope;
            if(coordinateStrings.contains( coordinate ))
            {
                return true;
            }
        }
        // check for scopeless, this probably can't happen
        return false;
    }

    public StringBuilder dfs( DependencyNode node, String start )
    {
        builder.append( start );
        builder.append( getCoordinateString( node ) );

        if ( visitedNodes.containsKey( node ) )
        {
            builder.append( " (Omitted due to cycle.)" ).append( System.lineSeparator() );
        }
        else if ( isDuplicateCoordinateString( node ) )
        {
            builder.append( " (Omitted due to duplicate artifact.)" ).append( System.lineSeparator() );
        }
        else if ( isScopeConflict( node ) )
        {
            builder.append( " (Omitted due to scope conflict.)" ).append( System.lineSeparator() );
        }
        else if ( isVersionConflict( node ) )
        {
            builder.append( " (Omitted due to version conflict.)" ).append( System.lineSeparator() );
        }
        else if ( node.getDependency().isOptional() )
        {
            builder.append( " (Omitted due to optional dependency.)" ).append( System.lineSeparator() );
        }
        else
        {
            coordinateStrings.add( getCoordinateString( node ) );
            versionlessCoordinateStrings.add( getVersionlessCoordinateString( node ) );
            builder.append( System.lineSeparator() );
            visitedNodes.put( node, true );

            for ( int i = 0; i < node.getChildren().size(); i++ )
            {
                if ( start.endsWith( "+- " ) )
                {
                    start = start.replace( "+- ", "|  " );
                }
                else if ( start.endsWith( "\\- " ) )
                {
                    start = start.replace( "\\- ", "   " );
                }

                if ( i == node.getChildren().size() - 1 )
                {
                    builder = dfs( node.getChildren().get( i ), start.concat( "\\- " ) );
                }
                else
                {
                    builder = dfs( node.getChildren().get( i ), start.concat( "+- " ) );
                }
            }
        }
        return builder;
    }
}
