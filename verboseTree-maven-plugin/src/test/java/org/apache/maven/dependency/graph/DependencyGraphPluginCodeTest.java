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

import dependency.AbstractDependencyMojoTestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Set;


public class DependencyGraphPluginCodeTest extends AbstractDependencyMojoTestCase
{

    @Before
    protected void setUp()
            throws Exception
    {
        // required for mojo lookups to work
        super.setUp( "tree", false );
    }

    @Test
    public void testTreeTestEnvironment()
            throws Exception
    {
        File testPom = new File( getBasedir(), "target/test-classes/tree-test/plugin-config.xml" );
        DependencyGraphBuilder mojo = (DependencyGraphBuilder) lookupMojo( "tree", testPom );


        assertNotNull( mojo );
        assertNotNull( mojo.getProject() );
        MavenProject project = mojo.getProject();
        project.setArtifact( this.stubFactory.createArtifact( "testGroupId", "project", "1.0" ) );

        MavenSession session = newMavenSession( project );
        setVariableValueToObject( mojo, "session", session );

        Set<Artifact> artifacts = this.stubFactory.getScopedArtifacts();
        Set<Artifact> directArtifacts = this.stubFactory.getReleaseAndSnapshotArtifacts();
        artifacts.addAll( directArtifacts );

        project.setArtifacts( artifacts );
        project.setDependencyArtifacts( directArtifacts );

        mojo.execute();

        org.eclipse.aether.graph.DependencyNode rootNode = mojo.getDependencyGraph();
        assertNodeEquals( "testGroupId:project:jar:1.0:compile", rootNode );
        assertEquals( 2, rootNode.getChildren().size() );
        assertChildNodeEquals( "testGroupId:snapshot:jar:2.0-SNAPSHOT:compile", rootNode, 0 );
        assertChildNodeEquals( "testGroupId:release:jar:1.0:compile", rootNode, 1 );
    }

    private void assertChildNodeEquals( String expectedNode, org.eclipse.aether.graph.DependencyNode actualParentNode, int actualChildIndex )
    {
        org.eclipse.aether.graph.DependencyNode actualNode = actualParentNode.getChildren().get( actualChildIndex );

        assertNodeEquals( expectedNode, actualNode );
    }

    private void assertNodeEquals( String expectedNode, org.eclipse.aether.graph.DependencyNode actualNode )
    {
        String[] tokens = expectedNode.split( ":" );

        assertNodeEquals( tokens[0], tokens[1], tokens[2], tokens[3], tokens[4], actualNode );
    }

    private void assertNodeEquals( String expectedGroupId, String expectedArtifactId, String expectedType,
                                   String expectedVersion, String expectedScope, org.eclipse.aether.graph.DependencyNode actualNode )
    {
        Artifact actualArtifact = (Artifact) actualNode.getArtifact();

        assertEquals( "group id", expectedGroupId, actualArtifact.getGroupId() );
        assertEquals( "artifact id", expectedArtifactId, actualArtifact.getArtifactId() );
        assertEquals( "type", expectedType, actualArtifact.getType() );
        assertEquals( "version", expectedVersion, actualArtifact.getVersion() );
        assertEquals( "scope", expectedScope, actualArtifact.getClassifier() );
    }
}
