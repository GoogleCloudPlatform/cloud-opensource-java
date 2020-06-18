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
import org.eclipse.aether.graph.DependencyNode;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.apache.commons.io.FileUtils.write;

/**
 * Gets the Project Building Request
 */
@Mojo( name = "tree")
public class DependencyGraphBuilder extends AbstractMojo
{

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    @Parameter
    private String outputFile;

    @Inject
    private ProjectDependenciesResolver resolver;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * The computed dependency tree root node of the Maven project.
     */
    private DependencyNode rootNode;

    public void execute() throws MojoExecutionException
    {
        getLog().info( project.getArtifactId() );
        getLog().info( session.toString() );

        File file = new File("C:\\Users\\ianla\\Maven\\cloud-opensource-java\\verboseTree-maven-plugin\\target\\its\\tree-verbose\\target\\tree.txt");
        try
        {
            write(file, "sample");
        }
        catch ( IOException e )
        {
            e.printStackTrace();
            getLog().error( "Failed to write to file" );
        }
    }

    public DependencyNode buildDependencyGraph()
    {
        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

        buildingRequest.setProject( project );


        final DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject( project );
        // request.setRepositorySession(  );

        // inal DependencyResolutionResult result =
        serialize();
        return null;
    }

    private void serialize()
    {
        File file = new File("C:\\Users\\ianla\\Maven\\cloud-opensource-java\\verboseTree-maven-plugin\\target\\its\\tree-verbose\\target\\tree.txt");
    }

    public DependencyNode getDependencyGraph()
    {
        return rootNode;
    }


    private DependencyResolutionResult resolveDependencies( DependencyResolutionRequest request,
                                                            Collection<MavenProject> reactorProjects )
            throws DependencyGraphBuilderException
    {
        try
        {
            return resolver.resolve( request );
        }
        catch ( DependencyResolutionException e )
        {
            if ( reactorProjects == null )
            {
                throw new DependencyGraphBuilderException( "Could not resolve following dependencies: "
                        + e.getResult().getUnresolvedDependencies(), e );
            }

            throw new DependencyGraphBuilderException(
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
}
