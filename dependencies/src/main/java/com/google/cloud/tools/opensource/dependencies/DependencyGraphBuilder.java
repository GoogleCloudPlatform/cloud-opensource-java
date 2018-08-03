/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.opensource.dependencies;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

/**
 * Based on the <a href="https://maven.apache.org/resolver/index.html">Apache Maven Artifact Resolver</a>
 * (formerly known as Eclipse Aether).
 */
public class DependencyGraphBuilder {
  
  private static final RepositorySystem system = newRepositorySystem();
  private static final RemoteRepository CENTRAL =
      new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();

  private static DependencyNode resolveCompileTimeDependencies(
      String groupId, String artifactId, String version)
      throws DependencyCollectionException, DependencyResolutionException {
    
    Artifact artifact = new DefaultArtifact(groupId + ':' + artifactId + ':' + version);

    return resolveCompileTimeDependencies(artifact);
  }
  
  // caching cuts time by about a factor of 4.
  private static final Map<String, DependencyNode> cache = new HashMap<>();

  private static DependencyNode resolveCompileTimeDependencies(Artifact artifact)
      throws DependencyCollectionException, DependencyResolutionException {
    
    String key = Artifacts.toCoordinates(artifact);
    if (cache.containsKey(key)) {
      return cache.get(key);
    }
    
    RepositorySystemSession session = newSession();

    Dependency dependency = new Dependency(artifact, "compile");

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(dependency);
    collectRequest.addRepository(CENTRAL);
    DependencyNode node = system.collectDependencies(session, collectRequest).getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(node);
    system.resolveDependencies(session, dependencyRequest);
    cache.put(key, node);
    
    return node;
  }

  private static RepositorySystemSession newSession() {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

    // todo find the local repository
    File temporaryDirectory = Files.createTempDir();
    temporaryDirectory.deleteOnExit();
    LocalRepository localRepository = new LocalRepository(temporaryDirectory.getAbsolutePath());
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepository));
    session.setReadOnly();
    return session;
  }

  private static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

    return locator.getService(RepositorySystem.class);
  }

  /**
   * Returns the non-transitive compile time dependencies of an artifact.
   */
  public static List<Artifact> getDirectDependencies(String groupId, String artifactId,
      String version) throws DependencyCollectionException, DependencyResolutionException {
    
    Preconditions.checkNotNull(groupId, "Group ID cannot be null");
    Preconditions.checkNotNull(artifactId, "Artifact ID cannot be null");
    Preconditions.checkNotNull(version, "Version cannot be null");
    
    List<Artifact> result = new ArrayList<>();
    
    DependencyNode node = resolveCompileTimeDependencies(groupId, artifactId, version);
    for (DependencyNode child : node.getChildren()) {
      result.add(child.getArtifact());
    }
    return result;
  }
  
  /**
   * Finds the full compile time, transitive dependency graph including duplicates
   * and conflicting versions. This method makes a lot of network connections
   * and runs for multiple minutes. Better support for local repos may help.
   */
  public static DependencyGraph getCompleteDependencies(String groupId, String artifactId,
      String version) throws DependencyCollectionException, DependencyResolutionException {
    
    // root node
    DependencyNode node = resolveCompileTimeDependencies(groupId, artifactId, version);  
    DependencyGraph graph = new DependencyGraph();
    fullPreorder(new Stack<DependencyNode>(), node, graph);    
    
    return graph;
  }
  
  /**
   * Finds the complete transitive dependency graph as seen by Maven.
   * It does not include duplicates and conflicting versions. That is,
   * this resolves conflicting versions by picking the first version
   * seen. This is how Maven normally operates.
   */
  public static DependencyGraph getTransitiveDependencies(String groupId, String artifactId,
      String version) throws DependencyCollectionException, DependencyResolutionException {
    
    // root node
    DependencyNode node = resolveCompileTimeDependencies(groupId, artifactId, version);  
    DependencyGraph graph = new DependencyGraph();
    preorder(new Stack<DependencyNode>(), node, graph);    
    
    return graph;
  }
  
  // TODO Dedup the next two methods. They are duplicate code with only one line difference.
  // this finds the actual graph that Maven sees with no duplicates and at most one version per
  // library.
  @SuppressWarnings("unchecked")
  private static void preorder(Stack<DependencyNode> path, DependencyNode current,
      DependencyGraph graph) {
    
    path.push(current);
    DependencyPath forPath = new DependencyPath();
    for (DependencyNode node : path) {
      forPath.add(node.getArtifact());
    }
    graph.addPath(forPath);
    
    for (DependencyNode child : current.getChildren()) {
      preorder((Stack<DependencyNode>) path.clone(), child, graph);
    }
  }

  @SuppressWarnings("unchecked")
  private static void fullPreorder(Stack<DependencyNode> path, DependencyNode current,
      DependencyGraph graph) throws DependencyCollectionException, DependencyResolutionException {
    
    path.push(current);
    
    DependencyPath forPath = new DependencyPath();
    for (DependencyNode node : path) {
      if (!"system".equals(node.getDependency().getScope())) {
        forPath.add(node.getArtifact());
      }
    }
    graph.addPath(forPath);
    
    for (DependencyNode child : current.getChildren()) {
      if (!"system".equals(child.getDependency().getScope())) {
        try {
          child = resolveCompileTimeDependencies(child.getArtifact());
        } catch (DependencyResolutionException ex) {
          System.err.println("Error resolving " + forPath);
          throw ex;
        }
        fullPreorder((Stack<DependencyNode>) path.clone(), child, graph);
      }
    }
  }

}
