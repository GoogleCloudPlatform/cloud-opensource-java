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

import com.google.cloud.tools.opensource.classpath.ClasspathCheckReport;
import com.google.cloud.tools.opensource.classpath.ClasspathChecker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

/**
 * Introduction to Maven Enforcer Plugin Custom Rule
 *
 * @see <a href="https://maven.apache.org/enforcer/enforcer-api/writing-a-custom-rule.html">Writing
 * a custom rule</a>
 */
public class ClasspathCheckerRule implements EnforcerRule {

  /** Simple param. This rule will fail if the value is true. */
  private boolean shouldIfail = false;

  private DependencyNode getNode(EnforcerRuleHelper helper) throws EnforcerRuleException {
    try {
      MavenProject project = (MavenProject) helper.evaluate("${project}");
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
          "Unable to lookup an expression " + e.getLocalizedMessage(), e);
    } catch (ComponentLookupException e) {
      throw new EnforcerRuleException("Unable to lookup a component " + e.getLocalizedMessage(), e);
    } catch (DependencyTreeBuilderException e) {
      throw new EnforcerRuleException(
          "Could not build dependency tree " + e.getLocalizedMessage(), e);
    }
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
      DependencyNode node = getNode(helper);

      log.info("Retrieved Target Folder: " + target);
      log.info("Retrieved ArtifactId: " + artifactId);
      log.info("Retrieved Project: " + project);
      log.info("Retrieved Project Dependencies: " + project.getDependencies());
      log.info("Retrieved Dependency Class: " + project.getDependencies().get(0).getClass());
      log.info("Retrieved Dependency Node: " + node);
      log.info("Retrieved RuntimeInfo: " + rti);
      log.info("Retrieved Session: " + session);
      log.info("Retrieved Resolver: " + resolver);

      List<Path> artifactsInPom = ImmutableList.of();
      List<Path> classpath = ImmutableList.of();
      List<Path> artifactJarsInBom = classpath.subList(0, artifactsInPom.size());
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
