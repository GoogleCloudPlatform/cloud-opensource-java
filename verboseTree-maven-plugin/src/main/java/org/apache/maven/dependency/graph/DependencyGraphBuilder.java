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
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.io.FileUtils.write;
import static org.apache.maven.dependency.graph.RepositoryUtility.CENTRAL;
import static org.apache.maven.dependency.graph.RepositoryUtility.mavenRepositoryFromUrl;

/**
 * Builds the DependencyGraph and for now outputs a text version of the dependency tree to a file
 */
@Mojo( name = "tree",
       requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class DependencyGraphBuilder extends AbstractMojo
{

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    @Parameter( defaultValue = "${repositorySystemSession}" )
    private RepositorySystemSession repositorySystemSession;

    @Parameter
    private String outputFile;

    @Inject
    private ProjectDependenciesResolver resolver;

    @Inject
    private ArtifactHandlerManager artifactHandlerManager;

    private SerializeGraph serializer;

    private static final RepositorySystem system = RepositoryUtility.newRepositorySystem();

    /**
     * Maven repositories to use when resolving dependencies.
     */
    private final List<RemoteRepository> repositories;
    private Path localRepository;

    public DependencyGraphBuilder()
    {
        this( Arrays.asList( CENTRAL.getUrl() ) );
    }

    static
    {
        for ( Map.Entry<String, String> entry : OsProperties.detectOsProperties().entrySet() )
        {
            System.setProperty( entry.getKey(), entry.getValue() );
        }
    }

    /**
     * @param mavenRepositoryUrls remote Maven repositories to search for dependencies
     * @throws IllegalArgumentException if a URL is malformed or does not have an allowed scheme
     */
    public DependencyGraphBuilder( Iterable<String> mavenRepositoryUrls )
    {
        List<RemoteRepository> repositoryList = new ArrayList<RemoteRepository>();
        for ( String mavenRepositoryUrl : mavenRepositoryUrls )
        {
            RemoteRepository repository = mavenRepositoryFromUrl( mavenRepositoryUrl );
            repositoryList.add( repository );
        }
        this.repositories = repositoryList;
    }

    public void execute() throws MojoExecutionException
    {
        File file = new File( project.getBasedir().getAbsolutePath().replace( '\\', '/' ) + "/target/tree.txt" );

        List<org.apache.maven.model.Dependency> dependencies = project.getDependencies();

        DependencyNode rootNode = buildFullDependencyGraph( dependencies );
        // rootNode is given compile Scope by default but should not have a scope
        DependencyNode prunedRoot = pruneTransitiveTestDependencies( rootNode );

        SerializeGraph serializer = new SerializeGraph();
        String serialized = serializer.serialize( prunedRoot );

        try
        {
            write( file, serialized );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
            getLog().error( "Failed to write to file:" + file.getAbsolutePath() );
        }
    }

    private Dependency getProjectDependency()
    {
        Model model = project.getModel();

        return new Dependency( new DefaultArtifact( model.getGroupId(), model.getArtifactId(), model.getPackaging(),
                        model.getVersion() ), "" );
    }

    private DependencyNode pruneTransitiveTestDependencies( DependencyNode rootNode )
    {
        Set<DependencyNode> visitedNodes = new HashSet<>();
        DependencyNode newRoot = new DefaultDependencyNode( getProjectDependency() );
        newRoot.setChildren( new ArrayList<DependencyNode>() );

        for ( int i = 0; i < rootNode.getChildren().size(); i++ )
        {
            DependencyNode childNode = rootNode.getChildren().get( i );
            newRoot.getChildren().add( childNode );

            dfs( childNode, visitedNodes );
        }

        return newRoot;
    }

    private void dfs( DependencyNode node , Set<DependencyNode> visitedNodes )
    {
        if ( !visitedNodes.contains( node ) )
        {
            visitedNodes.add( node );
            for ( int i = 0; i < node.getChildren().size(); i++ )
            {
                DependencyNode childNode = node.getChildren().get( i );

                if ( childNode.getDependency().getScope().equals( "test" ) )
                {
                    node.getChildren().remove( childNode );
                }

                dfs( childNode, visitedNodes );
            }
        }
    }

    private DependencyNode resolveCompileTimeDependencies( List<DependencyNode> dependencyNodes,
                                                           DefaultRepositorySystemSession session,
                                                           Dependency root )
            throws org.eclipse.aether.resolution.DependencyResolutionException
    {
        List<Dependency> dependencyList = new ArrayList<Dependency>();

        for ( DependencyNode dependencyNode : dependencyNodes )
        {
            dependencyList.add( dependencyNode.getDependency() );
        }

        if ( localRepository != null )
        {
            LocalRepository local = new LocalRepository( localRepository.toAbsolutePath().toString() );
            session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, local ) );
        }

        CollectRequest collectRequest = new CollectRequest();

        collectRequest.setRoot( dependencyList.get( 0 ) );
        if ( dependencyList.size() != 1 )
        {
            // With setRoot, the result includes dependencies with `optional:true` or `provided`
            collectRequest.setRoot(dependencyList.get(0));
            collectRequest.setDependencies( dependencyList );
        }
        else
        {
            collectRequest.setDependencies( dependencyList );
        }
        for ( RemoteRepository repository : repositories )
        {
            collectRequest.addRepository( repository );
        }
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest( collectRequest );
        // dependencyRequest.setRoot(  );

        // resolveDependencies equals to calling both collectDependencies (build dependency tree) and
        // resolveArtifacts (download JAR files).
        DependencyResult dependencyResult = system.resolveDependencies( session, dependencyRequest );
        return dependencyResult.getRoot();
    }

    /**
     * Finds the full compile time, transitive dependency graph including duplicates, conflicting versions, and provided
     * and optional dependencies. In the event of I/O errors, missing artifacts, and other problems, it can return an
     * incomplete graph. Each node's dependencies are resolved recursively. The scope of a dependency does not affect
     * the scope of its children's dependencies. Provided and optional dependencies are not treated differently than any
     * other dependency.
     *
     * @param artifacts Maven artifacts whose dependencies to retrieve
     * @return dependency graph representing the tree of Maven artifacts
     */
    private DependencyNode buildFullDependencyGraph( Set<org.apache.maven.artifact.Artifact> artifacts )
    {
        Dependency rootDependency = getProjectDependency();
        List<DependencyNode> dependencyNodes = new ArrayList<DependencyNode>();
        dependencyNodes.add( new DefaultDependencyNode( rootDependency ) );

        for ( org.apache.maven.artifact.Artifact artifact : artifacts )
        {
            Artifact newArtifact = new DefaultArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                    artifact.getClassifier(), artifact.getType(), artifact.getVersion() );
            newArtifact.setFile( artifact.getFile() );

            Dependency dependency = new Dependency( newArtifact, artifact.getScope() );
            DependencyNode node = new DefaultDependencyNode( dependency );
            dependencyNodes.add( node );
        }

        DefaultRepositorySystemSession session = RepositoryUtility.newSessionForFullDependency( system );
        return buildDependencyGraph( dependencyNodes, session, rootDependency );
    }

    private DependencyNode buildFullDependencyGraph( List<org.apache.maven.model.Dependency> dependencies )
    {
        Dependency rootDependency = getProjectDependency();
        List<DependencyNode> dependencyNodes = new ArrayList<DependencyNode>();
        dependencyNodes.add( new DefaultDependencyNode( rootDependency ) );

        for ( org.apache.maven.model.Dependency dependency : dependencies )
        {
            Artifact aetherArtifact = new DefaultArtifact( dependency.getGroupId(), dependency.getArtifactId(),
                    dependency.getClassifier(), dependency.getType(), dependency.getVersion() );

            Dependency aetherDependency = new Dependency( aetherArtifact , dependency.getScope() );
            DependencyNode node = new DefaultDependencyNode( aetherDependency );
            node.setOptional( dependency.isOptional() );
            dependencyNodes.add( node );
        }

        DefaultRepositorySystemSession session = RepositoryUtility.newSessionForFullDependency( system );
        return buildDependencyGraph( dependencyNodes, session, rootDependency );
    }

    private DependencyNode buildDependencyGraph( List<DependencyNode> dependencyNodes,
                                                 DefaultRepositorySystemSession session, Dependency root )
    {
        try
        {
            return resolveCompileTimeDependencies( dependencyNodes, session, root );
        }
        catch ( org.eclipse.aether.resolution.DependencyResolutionException ex )
        {
            DependencyResult result = ex.getResult();
            return result.getRoot();
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
