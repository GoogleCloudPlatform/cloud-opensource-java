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

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class SerializeGraphTest extends AbstractMojoTestCase
{
    private final SerializeGraph serializer = new SerializeGraph();

    @Test
    public void testBasicTree() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), null)
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );

        root.setChildren( Arrays.asList( left, right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/SerializerTests/BasicTree.txt");
        String expected = FileUtils.readFileToString( file );

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testLargeTree() throws IOException
    {
        // Construct nodes for tree l1 = level 1 with the root being l0
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), null )
        );
        DependencyNode l1left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode l1right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );
        DependencyNode l2left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.maven", "a4", "jar", "2.2.1" ), "system" )
        );
        DependencyNode l2middle = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "a5", "zip", "0" ), "import" )
        );
        DependencyNode l2right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.xyz", "a9", "xml", "1.2" ), "runtime" )
        );
        DependencyNode l3 = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.xyz", "a6", "xml", "1.2.1" ), "test" )
        );
        DependencyNode l4 = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.example", "a7", "jar", "2.2.2" ), "provided" )
        );
        DependencyNode l5right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.comm", "a7", "jar", "1" ), "compile" )
        );
        DependencyNode l5left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.comm", "a7", "jar", "1" ), "compile" )
        );
        DependencyNode l6left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.example", "a8", "xml", "2.1" ), "test" )
        );

        // Set Node Relationships
        l5left.setChildren( Arrays.asList( l6left ) );
        l4.setChildren( Arrays.asList( l5left, l5right ) );
        l3.setChildren( Arrays.asList( l4 ) );
        l2middle.setChildren( Arrays.asList( l3 ) );

        l1left.setChildren( Arrays.asList( l2left, l2middle ) );
        l1right.setChildren( Arrays.asList( l2right ) );

        root.setChildren( Arrays.asList( l1left, l1right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/SerializerTests/LargeTree.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testSmallGraphWithCycle() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), "")
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );

        root.setChildren( Arrays.asList( left, right ) );
        left.setChildren( Arrays.asList( root ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/SerializerTests/BasicCycle.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testLargeGraphWithCycles() throws IOException
    {
        // Construct nodes for tree l1 = level 1 with the root being l0
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), null )
        );
        DependencyNode l1left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test" )
        );
        DependencyNode l1right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );
        DependencyNode l2left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.maven", "a4", "jar", "2.2.1" ), "system" )
        );
        DependencyNode l2middle = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "a5", "zip", "0" ), "import" )
        );
        DependencyNode l2right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.xyz", "a9", "xml", "1.2" ), "runtime" )
        );
        DependencyNode l3 = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.xyz", "a6", "xml", "1.2.1" ), "test" )
        );
        DependencyNode l4 = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.example", "a7", "jar", "2.2.2" ), "provided" )
        );
        DependencyNode l5right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.comm", "a7", "jar", "1" ), "compile" )
        );
        DependencyNode l5left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.comm", "a7", "jar", "1" ), "compile" )
        );
        DependencyNode l6left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.example", "a8", "xml", "2.1" ), "test" )
        );

        // Set Node Relationships
        l5left.setChildren( Arrays.asList( l6left ) );
        l4.setChildren( Arrays.asList( l5left, l5right ) );
        l3.setChildren( Arrays.asList( l4 ) );
        l2middle.setChildren( Arrays.asList( l3 ) );

        l1left.setChildren( Arrays.asList( l2left, l2middle ) );
        l1right.setChildren( Arrays.asList( l2right ) );

        root.setChildren( Arrays.asList( l1left, l1right ) );

        // Introduce cycles
        l5left.setChildren( Arrays.asList( l2left, l1right, l3 ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/SerializerTests/LargeGraphWithCycles.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithOptional() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), "")
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test", true )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.xyz", "right", "zip", "1" ), "provided" )
        );

        root.setChildren( Arrays.asList( left, right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/SerializerTests/OptionalDependency.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithScopeConflict() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), "compile" )
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test", true )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), "test" )
        );

        root.setChildren( Arrays.asList( left, right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/SerializerTests/ScopeConflict.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testTreeWithVersionConflict() throws IOException
    {
        DependencyNode root = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "1.0.0" ), "compile" )
        );
        DependencyNode left = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "org.apache", "left", "xml", "0.1-SNAPSHOT" ), "test", true )
        );
        DependencyNode right = new DefaultDependencyNode(
                new Dependency( new DefaultArtifact( "com.google", "rootArtifact", "jar", "2.0.0" ), "test" )
        );

        root.setChildren( Arrays.asList( left, right ) );

        String actual = serializer.serialize( root );
        File file = new File(getBasedir(), "/target/test-classes/SerializerTests/VersionConflict.txt");
        String expected = FileUtils.readFileToString(file);

        Assert.assertEquals(expected, actual);
    }

}
