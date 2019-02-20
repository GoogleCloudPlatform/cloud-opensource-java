/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.opensource.enforcer;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.classpath.ClasspathCheckReport;
import com.google.cloud.tools.opensource.classpath.ClasspathChecker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.legacy.resolver.DefaultLegacyArtifactCollector;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.repository.ArtifactRepository;

/**
 * Introduction to Maven Enforcer Plugin Custom Rule
 *
 * @see <a href="https://maven.apache.org/enforcer/enforcer-api/writing-a-custom-rule.html">Writing
 * a custom rule</a>
 */
public class ClasspathCheckerRule implements EnforcerRule {

  /** Simple param. This rule will fail if the value is true. */
  private boolean shouldIfail = false;

  private DependencyNode getRootDependencyNode(MavenProject project, EnforcerRuleHelper helper)
      throws EnforcerRuleException {
    try {
      /* This throws ComponentLookupException
      RepositorySystemSession repositorySystemSession = helper.getComponent(RepositorySystemSession.class);
      */
      /* This only gives me 2 items */
      List<Dependency> dependencies = project.getDependencies();
      DependencyGraphBuilder dependencyGraphBuilder =
          helper.getComponent(DependencyGraphBuilder.class);

      /* With org.eclipse.aether.RepositorySystem, it gets DefaultRepositorySystem */
      RepositorySystem repositorySystem = helper.getComponent(RepositorySystem.class);

      DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

      /* With org.apache.maven.repository.RepositorySystem, it gets LegacyRepositorySystem
      RepositorySystem repositorySystem = helper.getComponent(RepositorySystem.class);*/
      //      List list = helper.getComponentList(null);

      /* No such thing
      RepositorySystemSession repositorySystemSession =
          helper.getComponent(RepositorySystemSession.class); */
      DependencyCollector dependencyCollector = helper.getComponent(DependencyCollector.class);


      /* No, this does not have Aether Artifact class
      org.eclipse.aether.artifact.Artifact aetherArtifact =
          helper.getComponent(org.eclipse.aether.artifact.Artifact.class);
      CollectRequest collectRequest = new CollectRequest();
      collectRequest.setRootArtifact(aetherArtifact); */

      dependencyCollector.collectDependencies(session, null);

      PlexusContainer container = helper.getContainer();
      Context context = container.getContext();
      DependencyNode dependencyNode = dependencyGraphBuilder.buildDependencyGraph(project, null);
      ArtifactRepository artifactRepository = helper.getComponent(ArtifactRepository.class);

      return dependencyNode;

      /* The following is from DependencyConvergenceRule
              DependencyTreeBuilder dependencyTreeBuilder =
            helper.getComponent(DependencyTreeBuilder.class);

        ArtifactRepository repository = (ArtifactRepository) helper.evaluate("${localRepository}");
        ArtifactFactory factory = helper.getComponent(ArtifactFactory.class);
        ArtifactMetadataSource metadataSource = helper.getComponent(ArtifactMetadataSource.class);
        ArtifactCollector collector = helper.getComponent(ArtifactCollector.class);
        ArtifactFilter filter = null; // we need to evaluate all scopes
        DependencyNode node =
            dependencyTreeBuilder.buildDependencyTree(
                project, repository, factory, metadataSource, filter, collector);
        return node;
      } catch (ExpressionEvaluationException e) {
        throw new EnforcerRuleException(
            "Unable to lookup an expression " + e.getLocalizedMessage(), e);*/
    } catch (ComponentLookupException e) {
      throw new EnforcerRuleException("Unable to lookup a component " + e.getLocalizedMessage(), e);
    } catch (DependencyCollectionException | DependencyGraphBuilderException e) {
      throw new EnforcerRuleException("Unable to build a dependency graph", e);
    }
  }

  private ImmutableList<Path> fetchClasspath(DependencyNode rootDependencyNode) {
    CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
    rootDependencyNode.accept(visitor);
    return visitor.getNodes().stream()
        .map(
            node -> {
              Artifact artifact = node.getArtifact();
              File file = artifact.getFile();
              if (file == null) {
                return null;
              }

              return file.toPath();
            })
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
    Log log = helper.getLog();

    try {
      // get the various expressions out of the helper.
      MavenProject project = (MavenProject) helper.evaluate("${project}");
      MavenSession session = (MavenSession) helper.evaluate("${session}");
      String target = (String) helper.evaluate("${project.build.directory}");
      String artifactId = (String) helper.evaluate("${project.artifactId}");

      // retrieve any component out of the session directly
      ArtifactResolver resolver = helper.getComponent(ArtifactResolver.class);
      RuntimeInformation rti = helper.getComponent(RuntimeInformation.class);

      // Does this work with cloud-opensource-java's DependencyNode?
      DependencyNode projectNode = getRootDependencyNode(project, helper);
      ImmutableList<Path> classpath = fetchClasspath(projectNode);

      log.info("Retrieved Target Folder: " + target);
      log.info("Retrieved ArtifactId: " + artifactId);
      log.info("Retrieved Project: " + project);
      log.info("Retrieved Project Dependencies: " + project.getDependencies());
      log.info("Retrieved Dependency Class: " + project.getDependencies().get(0).getClass());
      log.info("Retrieved Dependency Node: " + projectNode);
      log.info("Retrieved RuntimeInfo: " + rti);
      log.info("Retrieved Session: " + session);
      log.info("Retrieved Resolver: " + resolver);

      List<Path> artifactJarsInBom = classpath.subList(0, project.getDependencies().size());
      ImmutableSet<Path> entryPoints = ImmutableSet.copyOf(artifactJarsInBom);

      try {
        ClasspathChecker classpathChecker = ClasspathChecker.create(classpath, entryPoints);
        ClasspathCheckReport linkageReport = classpathChecker.findLinkageErrors();
        log.info("Generated linkage error report: " + linkageReport);
      } catch (IOException ex) {
        log.error("Failed to run Classpath Checker", ex);
      }

      if (this.shouldIfail) {
        throw new EnforcerRuleException("Failing because my param said so.");
      }
    } catch (ComponentLookupException e) {
      throw new EnforcerRuleException("Unable to lookup a component " + e.getLocalizedMessage(), e);
    } catch (ExpressionEvaluationException e) {
      throw new EnforcerRuleException(
          "Unable to lookup an expression " + e.getLocalizedMessage(), e);
    }
  }

  /**
   * If your rule is cacheable, you must return a unique id when parameters or conditions change
   * that would cause the result to be different. Multiple cached results are stored based on their
   * id.
   *
   * <p>The easiest way to do this is to return a hash computed from the values of your parameters.
   *
   * <p>If your rule is not cacheable, then the result here is not important, you may return
   * anything.
   */
  public String getCacheId() {
    // no hash on boolean...only parameter so no hash is needed.
    return "" + this.shouldIfail;
  }

  /**
   * This tells the system if the results are cacheable at all. Keep in mind that during forked
   * builds and other things, a given rule may be executed more than once for the same project. This
   * means that even things that change from project to project may still be cacheable in certain
   * instances.
   */
  public boolean isCacheable() {
    return false;
  }

  /**
   * If the rule is cacheable and the same id is found in the cache, the stored results are passed
   * to this method to allow double checking of the results. Most of the time this can be done by
   * generating unique ids, but sometimes the results of objects returned by the helper need to be
   * queried. You may for example, store certain objects in your rule and then query them later.
   */
  public boolean isResultValid(EnforcerRule arg0) {
    return false;
  }
}
