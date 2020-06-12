/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import org.apache.maven.artifact.Artifact;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import org.apache.maven.execution.MavenSession;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import org.eclipse.aether.DefaultRepositorySystemSession;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import org.example.mojo.GetBuildingRequestMojo;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import java.util.HashSet;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import java.util.Set;

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import static org.mockito.Mockito.mock;
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  import static org.mockito.Mockito.when;

public class GetBuildingRequestMojoTest extends AbstractMojoTestCase
{
    GetBuildingRequestMojo mojo;

    protected void setUp() throws Exception
    {
        // required for mojo lookups to work
        super.setUp();

        File testPom = new File( getBasedir(),
                "target/test-classes/unit/GetBuildingRequestMojo.xml" );
        mojo = (GetBuildingRequestMojo) lookupMojo("getBuildingRequest", testPom);

        assertNotNull( mojo );
        assertNotNull( mojo.getProject() );
        MavenProject project = mojo.getProject();

        MavenSession session = newMavenSession( project );
        setVariableValueToObject( mojo, "session", session );

        DefaultRepositorySystemSession repoSession = (DefaultRepositorySystemSession) session.getRepositorySession();
        //repoSession.setLocalRepositoryManager( new SimpleLocalRepositoryManager( stubFactory.getWorkingDir() ) );

        Artifact mockArtifact = mock(Artifact.class);
        when(mockArtifact.getArtifactId()).thenReturn( "verboseTree-maven-plugin" );
        Set<Artifact> set = new HashSet<Artifact>();
        set.add( mockArtifact );

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
