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

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

import java.io.StringWriter;
import java.io.Writer;

public class SerializeGraph
{
    // will be injected eventually
    private String outputType;

    public String serialize( DependencyNode root, String outputType )
    {
        this.outputType = outputType;
        StringWriter writer = new StringWriter();

        DependencyVisitor visitor;
        return "";
    }

    /**
     * @param writer {@link Writer}
     * @return {@link DependencyVisitor}
     */
    public DependencyVisitor getSerializingDependencyNodeVisitor( Writer writer )
    {
        if ( "graphml".equals( outputType ) )
        {
            return new GraphmlDependencyNodeVisitor( writer );
        }
        else if ( "tgf".equals( outputType ) )
        {
            return new TGFDependencyNodeVisitor( writer );
        }
        else if ( "dot".equals( outputType ) )
        {
            return new DOTDependencyNodeVisitor( writer );
        }
        else
        {
            return new SerializingDependencyNodeVisitor( writer, toGraphTokens( tokens ) );
        }
    }
}
