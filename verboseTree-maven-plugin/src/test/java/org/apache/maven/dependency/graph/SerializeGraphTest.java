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

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SerializeGraphTest extends AbstractMojoTestCase
{
    private SerializeGraph serializer;

    @Before
    public void setUp()
    {
        serializer = new SerializeGraph();
    }

    @Test
    public void testBasicTree()
    {
        Artifact rootArtifact = new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0");
        Artifact leftChildArtifact = new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" );
        Artifact rightChildArtifact = new DefaultArtifact( "org.xyz", "right", "zip", "1" );

        DefaultDependencyNode root = new DefaultDependencyNode( rootArtifact );
        DefaultDependencyNode left = new DefaultDependencyNode( leftChildArtifact );
        DefaultDependencyNode right = new DefaultDependencyNode( rightChildArtifact );

        List<DependencyNode> rootChildren = new ArrayList<DependencyNode>();
        rootChildren.add( left );
        rootChildren.add( right );

        root.setChildren( rootChildren );
    }
}
