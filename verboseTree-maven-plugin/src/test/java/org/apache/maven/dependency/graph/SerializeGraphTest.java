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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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

        DependencyNode root = new DefaultDependencyNode( rootArtifact );
        DependencyNode left = new DefaultDependencyNode( leftChildArtifact );
        DependencyNode right = new DefaultDependencyNode( rightChildArtifact );

        List<DependencyNode> rootChildren = new ArrayList<DependencyNode>();
        rootChildren.add( left );
        rootChildren.add( right );

        root.setChildren( rootChildren );

        String result = serializer.serialize( root, "" );
    }

    @Test
    public void testLargeTree() throws IOException
    {
        Artifact rootArtifact = new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0");
        Artifact leftChildArtifact = new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" );
        Artifact rightChildArtifact = new DefaultArtifact( "org.xyz", "right", "zip", "1" );

        DependencyNode root = new DefaultDependencyNode( rootArtifact );
        DependencyNode l1left = new DefaultDependencyNode( leftChildArtifact );
        DependencyNode l1right = new DefaultDependencyNode( rightChildArtifact );
        DependencyNode l2left = new DefaultDependencyNode(
                new DefaultArtifact( "org.maven", "a4", "jar", "2.2.1" )
        );
        DependencyNode l2middle = new DefaultDependencyNode(
                new DefaultArtifact( "com.google", "a5", "zip", "0" )
        );
        DependencyNode l2right = new DefaultDependencyNode(
                new DefaultArtifact( "com.xyz", "a9", "xml", "1.2" )
        );
        DependencyNode l3 = new DefaultDependencyNode(
                new DefaultArtifact( "com.xyz", "a6", "xml", "1.2.1" )
        );
        DependencyNode l4 = new DefaultDependencyNode(
                new DefaultArtifact( "com.example", "a7", "jar", "2.2.2" )
        );
        DependencyNode l5 = new DefaultDependencyNode(
                new DefaultArtifact( "com.comm", "a7", "jar", "1" )
        );

        // chain of children
        l4.setChildren( Arrays.asList( l5 ) );
        l3.setChildren( Arrays.asList( l4 ) );
        l2middle.setChildren( Arrays.asList( l3 ) );

        l1left.setChildren( Arrays.asList( l2left, l2middle ) );
        l1right.setChildren( Arrays.asList( l2right ) );

        root.setChildren( Arrays.asList( l1left, l1right ) );

        String result = serializer.serialize( root, "" );
        String expected = readFile( getBasedir() + "/target/test-classes/SerializerTests/LargeTree.txt",
                Charset.defaultCharset() );

        Assert.assertEquals(expected, result);
    }

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes( Paths.get(path));
        return new String(encoded, encoding);
    }

    @Test
    public void testGraphWithCycle()
    {

    }

    @Test
    public void testTreeWithDuplicates()
    {

    }
}
