package com.google.cloud.tools.opensource.dependencies;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

public class Dependencies {
  public static void main(String[] args) throws DependencyCollectionException, DependencyResolutionException {
  
// Apache Maven Artifact Resolver

    RepositorySystem repoSystem = newRepositorySystem();
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    // session.setDependencyTraverser(dependencyTraverser);

    LocalRepository localRepo = new LocalRepository("target/local-repo");
    session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(session, localRepo));

    Artifact artifact = new DefaultArtifact("com.google.cloud:google-cloud-bigquery:1.37.1");
    Dependency dependency = new Dependency(artifact, "compile");
    RemoteRepository central =
        new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/")
            .build();

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(dependency);
    collectRequest.addRepository(central);
    org.eclipse.aether.graph.DependencyNode node =
        repoSystem.collectDependencies(session, collectRequest).getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(node);

    repoSystem.resolveDependencies(session, dependencyRequest);

    PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
    node.accept(nlg);
    System.out.println(nlg.getClassPath());
    
    for (DependencyNode dep : node.getChildren()) {
      System.out.println(dep.toString());
    }
  }

  private static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

    return locator.getService(RepositorySystem.class);
  }
}
