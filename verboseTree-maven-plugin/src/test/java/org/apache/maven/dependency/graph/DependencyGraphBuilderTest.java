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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.Test;
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

        Artifact artifact = new DefaultArtifact( "org.com.google","artifactId", "1.0.0", "compile", "jar", "C1",
                null);
        Set<Artifact> set = new HashSet<Artifact>();
        set.add( artifact );

        project.setArtifacts( set );
        project.setArtifactId( "id" );

        ArtifactHandlerManager manager = lookup( ArtifactHandlerManager.class );
        setVariableValueToObject( mojo, "artifactHandlerManager", manager );
    }

    @Test
    public void testGetBuildingRequestTestEnvironment() throws Exception
    {
        mojo.execute();
        // ToDo: when actual functionality is added make sure the artifact is what we expect before running other tests
    }
}
