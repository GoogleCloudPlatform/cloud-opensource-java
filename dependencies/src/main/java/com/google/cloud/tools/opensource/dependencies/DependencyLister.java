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
import java.util.List;
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
 * Based on <a href="https://maven.apache.org/resolver/index.html">Apache Maven Artifact Resolver</a>
 * (formerly known as Eclipse Aether).
 */
public class DependencyLister {
  
  private static final RepositorySystem SYSTEM = newRepositorySystem();
  private static final RemoteRepository CENTRAL =
      new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();

  public static void main(String[] args)
      throws DependencyCollectionException, DependencyResolutionException {
    
    if (args.length != 1 || !args[0].contains(":")) {
      System.err.println("Usage: java " + DependencyLister.class.getCanonicalName()
          + " groupdId:artifactId:version");
      return;
    }
    
    String[] coordinates = args[0].split(":");
    String groupId = coordinates[0];
    String artifactId = coordinates[1];
    String version = coordinates[2];
    
    DependencyGraph graph = getFullDependencyPath(groupId, artifactId, version);
    
    List<DependencyPath> paths = graph.list();
    for (DependencyPath path : paths) { 
      System.err.println(path);
    }

    DependencyNode node = resolveCompileTimeDependencies(groupId, artifactId, version);
    
    for (DependencyNode dependency : node.getChildren()) {
      System.out.println(dependency.toString());
    }
  }

  private static DependencyNode resolveCompileTimeDependencies(
      String groupId, String artifactId, String version)
      throws DependencyCollectionException, DependencyResolutionException {
    
    Artifact artifact = new DefaultArtifact(groupId + ':' + artifactId + ':' + version);

    return resolveCompileTimeDependencies(artifact);
  }

  private static DependencyNode resolveCompileTimeDependencies(Artifact artifact)
      throws DependencyCollectionException, DependencyResolutionException {
    RepositorySystemSession session = newSession();

    Dependency dependency = new Dependency(artifact, "compile");

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(dependency);
    collectRequest.addRepository(CENTRAL);
    DependencyNode node = SYSTEM.collectDependencies(session, collectRequest).getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(node);

    SYSTEM.resolveDependencies(session, dependencyRequest);
    return node;
  }

  private static RepositorySystemSession newSession() {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

    // todo find the local repository
    File temporaryDirectory = Files.createTempDir();
    temporaryDirectory.deleteOnExit();
    LocalRepository localRepository = new LocalRepository(temporaryDirectory.getAbsolutePath());
    session.setLocalRepositoryManager(SYSTEM.newLocalRepositoryManager(session, localRepository));
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
   * Returns the direct compile time dependencies of an artifact.
   */
  public static List<Artifact> getImmediateDependencies(String groupId, String artifactId, String version) 
      throws DependencyCollectionException, DependencyResolutionException {
    
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
   * Finds the complete transitive dependency graph with duplicates and conflicting versions.
   * This method makes a lot of network connections
   * and runs for multiple minutes. Better support for local repos may help.
   */
  public static DependencyGraph getFullDependencyPath(String groupId, String artifactId,
      String version) throws DependencyCollectionException, DependencyResolutionException {
    
    // root node
    DependencyNode node = resolveCompileTimeDependencies(groupId, artifactId, version);  
    DependencyGraph graph = new DependencyGraph();
    fullPreorder(new Stack<DependencyNode>(), node, graph);    
    
    return graph;
  }
  
  /**
   * Finds the complete transitive dependency graph with duplicates and conflicting versions.
   * This method makes a lot of network connections
   * and runs for multiple minutes. Better support for local repos may help.
   */
  public static DependencyGraph getTransitiveDependencies(String groupId, String artifactId,
      String version) throws DependencyCollectionException, DependencyResolutionException {
    
    // root node
    DependencyNode node = resolveCompileTimeDependencies(groupId, artifactId, version);  
    DependencyGraph graph = new DependencyGraph();
    preorder(new Stack<DependencyNode>(), node, graph);    
    
    return graph;
  }
  
  // this finds the actual graph that maven sees with no duplicates and at most one version per
  // library.
  private static void preorder(Stack<DependencyNode> path, DependencyNode node, DependencyGraph graph) {
    path.push(node);
    DependencyPath forPath = new DependencyPath();
    for (DependencyNode dn : path) {
      forPath.add(dn.getArtifact());
    }
    graph.addPath(forPath);
    
    for (DependencyNode child : node.getChildren()) {
      preorder((Stack<DependencyNode>) path.clone(), child, graph);
    }
  }

  private static void fullPreorder(Stack<DependencyNode> path, DependencyNode node, DependencyGraph graph) 
      throws DependencyCollectionException, DependencyResolutionException {
    
    path.push(node);
    
    DependencyPath forPath = new DependencyPath();
    for (DependencyNode dn : path) {
      forPath.add(dn.getArtifact());
    }
    graph.addPath(forPath);
    
    for (DependencyNode child : node.getChildren()) {
      child = resolveCompileTimeDependencies(child.getArtifact());
      fullPreorder((Stack<DependencyNode>) path.clone(), child, graph);
    }
  }

}
