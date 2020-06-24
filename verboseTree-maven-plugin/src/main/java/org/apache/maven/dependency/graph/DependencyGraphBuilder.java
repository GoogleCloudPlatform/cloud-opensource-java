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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.apache.commons.io.FileUtils.write;

/**
 * Builds the DependencyGraph
 */
@Mojo( name = "tree" )
public class DependencyGraphBuilder extends AbstractMojo
{

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    @Parameter
    private String outputFile;

    @Component
    private ProjectDependenciesResolver resolver;

    // replace Component with sisu guice named or singleton annotation
    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    private SerializeGraph serializer;

    private DependencyNode rootNode;

    @Parameter( defaultValue = "${repositorySystemSession}" )
    private RepositorySystemSession repositorySystemSession;

    public void execute() throws MojoExecutionException
    {
        getLog().info( project.getArtifactId() );
        getLog().info( session.toString() );

        try
        {
            rootNode = buildDependencyGraph();
        }
        catch ( Exception e )
        {
            // ToDo: Better error message and Exception type
            e.printStackTrace();
        }

        // ToDo: if outputFile not null write to outputFile
        File file = new File( project.getBasedir().getAbsolutePath().replace( '\\', '/' ) + "/target/tree.txt" );

        SerializeGraph serializer = new SerializeGraph();
        String serialized = serializer.serialize( rootNode );
        try
        {
            write( file, project.getBasedir().getAbsolutePath() );
            /*write( file, rootNode.getArtifact() + "\n" + rootNode.getChildren().get( 0 ).getArtifact() +
                    rootNode.getChildren().get( 0 ).getDependency().getScope());*/
            /*write( file, "why" + rootNode.toString() + " " + rootNode.getChildren().get( 0 ).toString()
            + " " + rootNode.getChildren().get( 1 ).toString() + "\n" +
                    rootNode.getChildren().get( 2 ).toString() + " " + rootNode.getChildren().get( 3 ).toString()
            + " " + rootNode.getChildren().get( 0 ).getChildren().size()
                    + " " + rootNode.getChildren().get( 1 ).getChildren().size()
                    + " " + rootNode.getChildren().get( 2 ).getChildren().size()
                    + " " + rootNode.getChildren().get( 3 ).getChildren().size());*/
            // write(file, serialized);
        }
        catch ( IOException | NullPointerException e )
        {
            e.printStackTrace();
            getLog().error( "Failed to write to file:" + file.getAbsolutePath() );
        }
    }

    public DependencyNode buildDependencyGraph() throws Exception
    {
        // adapting the dependency-plugin code
        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

        buildingRequest.setProject( project );

        // need to configure the repositorySystemSession

        // dependency plugin code that isn't needed below
        // dependencyGraphBuilder.buildDependencyGraph( buildingRequest, artifactFilter, reactorProjects );

        // now adapting the dependency-tree defaultDependencyGraphBuilder and maven31Code

        final DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject( project );
        request.setRepositorySession( session.getRepositorySession() );
        // request.setRepositorySession( repositorySystemSession );

        final DependencyResolutionResult result = resolveDependencies( request, null );
        DependencyNode graphRoot = result.getDependencyGraph();


        return graphRoot;
    }


    public DependencyNode getDependencyGraph()
    {
        return rootNode;
    }


    private DependencyResolutionResult resolveDependencies( DependencyResolutionRequest request,
                                                            Collection<MavenProject> reactorProjects )
            throws Exception
    {
        try
        {
            return resolver.resolve( request );
        }
        catch ( DependencyResolutionException e )
        {
            if ( reactorProjects == null )
            {
                throw new Exception( "Could not resolve following dependencies: "
                        + e.getResult().getUnresolvedDependencies(), e );
            }

            throw new Exception(
                    "REACTOR NOT SUPPORTED YET. Could not resolve following dependencies: "
                    + e.getResult().getUnresolvedDependencies(), e );

            // ToDo: try collecting from reactor for multi module project
            // return collectDependenciesFromReactor( e, reactorProjects );
        }
    }

    /**
     * Gets the Maven project used by this mojo.
     *
     * @return the Maven project
     */
    public MavenProject getProject()
    {
        return project;
    }

    public RepositorySystemSession getRepositorySystemSession()
    {
        return repositorySystemSession;
    }
}
