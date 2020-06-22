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

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
// doesn't work with maven project import org.eclipse.aether.artifact.Artifact;
// import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.junit.Test;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class DependencyGraphBuilderTest extends AbstractMojoTestCase
{
    private DependencyGraphBuilder mojo;

    protected void setUp() throws Exception
    {
        // required for mojo lookups to work
        super.setUp();

        File testPom = new File( getBasedir(),
                "target/test-classes/DependencyGraphBuilderTest/DependencyGraphBuilderTest.xml" );
        mojo = (DependencyGraphBuilder) lookupMojo( "tree", testPom );

        assertNotNull( mojo );
        assertNotNull( mojo.getProject() );
        MavenProject project = mojo.getProject();

        MavenSession session = newMavenSession( project );
        setVariableValueToObject( mojo, "session", session );

        DefaultRepositorySystemSession repoSession = (DefaultRepositorySystemSession) session.getRepositorySession();
        //repoSession.setLocalRepositoryManager( new SimpleLocalRepositoryManager( stubFactory.getWorkingDir() ) );

        Artifact artifact = new DefaultArtifact(
                "org.com.google", "artifactId", "1.0.0", "compile", "jar", "classifier1", null );
        // add properties

        Set<org.apache.maven.artifact.Artifact> set = new HashSet<org.apache.maven.artifact.Artifact>();
        set.add( (org.apache.maven.artifact.Artifact) artifact );

        project.setArtifacts( set );
        project.setArtifactId( "id" );

        ArtifactHandlerManager manager = lookup( ArtifactHandlerManager.class );
        setVariableValueToObject( mojo, "artifactHandlerManager", manager );
    }


    @Test
    public void testGetBuildingRequestTestEnvironment() throws Exception
    {
        super.setUp();

        File testPom = new File( getBasedir(),
                "target/test-classes/DependencyGraphBuilderTest/DependencyGraphBuilderTest.xml" );
        mojo = (DependencyGraphBuilder) lookupMojo( "tree", testPom );

        assertNotNull( mojo );
        assertNotNull( mojo.getProject() );
        MavenProject project = mojo.getProject();

        MavenSession session = newMavenSession( project );
        setVariableValueToObject( mojo, "session", session );

        DefaultRepositorySystemSession repoSession = (DefaultRepositorySystemSession) session.getRepositorySession();


        org.apache.maven.artifact.Artifact artifact = new org.apache.maven.artifact.DefaultArtifact(
                "org.com.google", "artifactId", "1.0.0", "compile", "jar", "classifier1", null );
        //artifact.setFile(  );
        ArtifactHandler handler = new DefaultArtifactHandler();
        artifact.setArtifactHandler( handler );



        Set<org.apache.maven.artifact.Artifact> artifacts;

        ArtifactHandlerManager manager = lookup( ArtifactHandlerManager.class );
       // setVariableValueToObject( manager, "extension", "ok" );
        setVariableValueToObject( mojo, "artifactHandlerManager", manager );

        mojo.execute();


        DependencyNode rootNode = mojo.getDependencyGraph();
        assertNodeEquals( "testGroupId:project:jar:1.0:compile", rootNode );
        assertEquals( 2, rootNode.getChildren().size() );
        assertChildNodeEquals( "testGroupId:snapshot:jar:2.0-SNAPSHOT:compile", rootNode, 0 );
        assertChildNodeEquals( "testGroupId:release:jar:1.0:compile", rootNode, 1 );
        // ToDo: when actual functionality is added make sure the artifact is what we expect before running other tests
    }

    private void assertChildNodeEquals( String expectedNode, DependencyNode actualParentNode, int actualChildIndex )
    {
        DependencyNode actualNode = actualParentNode.getChildren().get( actualChildIndex );

        assertNodeEquals( expectedNode, actualNode );
    }

    private void assertNodeEquals( String expectedNode, DependencyNode actualNode )
    {
        String[] tokens = expectedNode.split( ":" );

        assertNodeEquals( tokens[0], tokens[1], tokens[2], tokens[3], tokens[4], actualNode );
    }

    private void assertNodeEquals( String expectedGroupId, String expectedArtifactId, String expectedType,
                                   String expectedVersion, String expectedScope, DependencyNode actualNode )
    {
        Artifact actualArtifact = (Artifact) actualNode.getArtifact();

        assertEquals( "group id", expectedGroupId, actualArtifact.getGroupId() );
        assertEquals( "artifact id", expectedArtifactId, actualArtifact.getArtifactId() );
        assertEquals( "type", expectedType, actualArtifact.getType() );
        assertEquals( "version", expectedVersion, actualArtifact.getVersion() );
        assertEquals( "scope", expectedScope, actualArtifact.getClassifier() );
    }
}
