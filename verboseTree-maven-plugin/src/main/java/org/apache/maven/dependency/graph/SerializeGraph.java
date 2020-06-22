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

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class SerializeGraph
{
    // will be injected eventually
    private String outputType;

    private final Map<DependencyNode, Boolean> visitedNodes;
    private final Set<String> artifactSet;
    private StringBuilder builder;

    public SerializeGraph()
    {
        visitedNodes = new IdentityHashMap<DependencyNode, Boolean>( 512 );
        artifactSet = new HashSet<String>();
        builder = new StringBuilder();
    }

    public String serialize( DependencyNode root )
    {
        return dfs( root, "" ).toString();
    }

    private static void appendDependency( StringBuilder builder, DependencyNode node )
    {
        String scope = node.getDependency().getScope();
        builder.append( getCoordinateString( node.getArtifact() ) );

        if ( scope != null && !scope.isEmpty() )
        {
            builder.append( ":" ).append( scope );
        }
    }

    private static String getCoordinateString( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" +
                artifact.getExtension() + ":" + artifact.getVersion();
    }

    private boolean isDuplicateArtifact( Artifact artifact )
    {
        String coordinateString = getCoordinateString( artifact );
        return artifactSet.contains( coordinateString );
    }

    public StringBuilder dfs( DependencyNode node, String start )
    {
        builder.append( start );
        appendDependency( builder, node );

        if ( visitedNodes.containsKey( node ) )
        {
            builder.append( " (Omitted due to cycle)" ).append( System.lineSeparator() );
        }
        else if ( isDuplicateArtifact( node.getArtifact() ) )
        {
            builder.append( " (Omitted due to duplicate artifact)" ).append( System.lineSeparator() );
        }
        else if ( node.getDependency().isOptional() )
        {
            builder.append( " (Omitted due to optional dependency)" ).append( System.lineSeparator() );
        }
        else
        {
            artifactSet.add( getCoordinateString( node.getArtifact() ) );
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
