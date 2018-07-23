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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

/**
 * Based on Apache Maven Artifact Resolver; formerly Eclipse Aether.
 */
public class Dependencies {
  
  public static void main(String[] args) throws DependencyCollectionException, DependencyResolutionException {

    DependencyNode node = resolveNode("com.google.cloud", "google-cloud-bigquery", "1.37.1");

    PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
    node.accept(nlg);
    System.out.println(nlg.getClassPath());
    
    for (DependencyNode dep : node.getChildren()) {
      System.out.println(dep.toString());
    }
  }

  private static DependencyNode resolveNode(String groupId, String artifactId, String version)
      throws DependencyCollectionException, DependencyResolutionException {
    RepositorySystem repoSystem = newRepositorySystem();
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

    LocalRepository localRepo = new LocalRepository("target/local-repo");
    session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(session, localRepo));

    Artifact artifact = new DefaultArtifact(groupId + ':' + artifactId + ':' + version);
    Dependency dependency = new Dependency(artifact, "compile");
    RemoteRepository central =
        new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/")
            .build();

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(dependency);
    collectRequest.addRepository(central);
    DependencyNode node = repoSystem.collectDependencies(session, collectRequest).getRoot();

    DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setRoot(node);

    repoSystem.resolveDependencies(session, dependencyRequest);
    return node;
  }

  private static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

    return locator.getService(RepositorySystem.class);
  }

  public static List<Artifact> getDependencies(String groupId, String artifactId, String version) 
      throws DependencyCollectionException, DependencyResolutionException {
    List<Artifact> result = new ArrayList<>();
    
    DependencyNode node = resolveNode(groupId, artifactId, version);
    for (DependencyNode child : node.getChildren()) {
      result.add(child.getArtifact());
    }
    return result;
  }
}
