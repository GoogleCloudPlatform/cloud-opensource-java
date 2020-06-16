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
import org.eclipse.aether.graph.DependencyVisitor;

import java.io.StringWriter;
import java.io.Writer;
import java.util.IdentityHashMap;
import java.util.Map;

public class SerializeGraph
{
    // will be injected eventually
    private String outputType;

    public String serialize( DependencyNode root, String outputType )
    {
        this.outputType = outputType;
        StringBuilder builder = new StringBuilder();
        Map<DependencyNode, Boolean> visitedNodes = new IdentityHashMap<DependencyNode, Boolean>(512);

        return dfs( root, builder, visitedNodes, 0, "" ).toString();
    }

    public StringBuilder dfs( DependencyNode node, StringBuilder builder, Map<DependencyNode, Boolean> visitedNodes,
                              int level, String start )
    {
        visitedNodes.put( node, true );

        Artifact nodeArtifact = node.getArtifact();

        for(int i = 0; i < level; ++i )
        {
            builder.append( "|  " );
        }
        builder.append( start );
        builder.append( nodeArtifact.getGroupId() + ":" + nodeArtifact.getArtifactId() +
                ":" + nodeArtifact.getExtension() + ":" + nodeArtifact.getVersion() + System.lineSeparator());


        for ( int i = 0; i < node.getChildren().size(); ++i )
        {
            if(i == node.getChildren().size() - 1)
            {
                builder = dfs(node.getChildren().get( i ), builder, visitedNodes, level + 1, "\\-");
            }
            else
            {
                builder = dfs(node.getChildren().get( i ), builder, visitedNodes, level + 1, "+-");
            }
        }

        return builder;
    }


}
