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

package com.google.cloud.tools.dependencies.gradle;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.io.IOException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;

/**
 * Task to run Linkage Checker for the dependencies of the Gradle project.
 */
public class LinkageCheckTask extends DefaultTask {

  Logger logger;

  @TaskAction
  public void run() throws IOException {
    logger = getLogger();

    LinkageCheckerPluginExtension extension =
        getProject().getExtensions().findByType(LinkageCheckerPluginExtension.class);
    if (extension == null) {
      extension = new LinkageCheckerPluginExtension();
    }

    Project project = getProject();
    ImmutableSet.Builder<Configuration> configurationsBuilder = ImmutableSet.builder();
    if (extension.getConfigurations().isEmpty()) {
      // Should this default to runtime?
      logger.trace("No configuration specified, defaulting to all configurations");
      for (Configuration configuration : project.getConfigurations()) {
        if (configuration.isCanBeResolved()) {
          configurationsBuilder.add(configuration);
        }
      }
    } else {
      for (String configurationName : extension.getConfigurations()) {
        Configuration configuration = project.getConfigurations().getByName(configurationName);
        if (configuration.isCanBeResolved()) {
          configurationsBuilder.add(configuration);
        }
      }
    }

    ImmutableSet<Configuration> configurations = configurationsBuilder.build();

    boolean foundError = false;
    for (Configuration configuration : configurations) {
      logger.info("Checking {}", configuration);
      ImmutableList.Builder<ClassPathEntry> classPathBuilder = ImmutableList.builder();

      // TODO(suztomo): Should this include optional dependencies?
      //  Once we decide what to do with the optional dependencies, let's revisit this logic.
      for (ResolvedArtifact resolvedArtifact :
          configuration.getResolvedConfiguration().getResolvedArtifacts()) {
        ModuleVersionIdentifier moduleVersionId = resolvedArtifact.getModuleVersion().getId();
        DefaultArtifact artifact =
            new DefaultArtifact(
                moduleVersionId.getGroup(),
                moduleVersionId.getName(),
                null,
                null,
                moduleVersionId.getVersion(),
                null,
                resolvedArtifact.getFile());
        classPathBuilder.add(new ClassPathEntry(artifact));
      }

      ImmutableList<ClassPathEntry> classPath = classPathBuilder.build();
      if (classPath.isEmpty()) {
        // No artifact for this configuration
        continue;
      }
      LinkageChecker linkageChecker = LinkageChecker.create(classPath);

      ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
          linkageChecker.findSymbolProblems();

      int errorCount = symbolProblems.keySet().size();

      // TODO(suztomo): Show the dependency paths to the problematic artifacts.
      logger.error(
          "Linkage Checker rule found {} error{}. Linkage error report:\n{}",
          errorCount,
          errorCount > 1 ? "s" : "",
          SymbolProblem.formatSymbolProblems(symbolProblems));
      foundError |= errorCount > 0;
    }

    if (foundError) {
      throw new GradleException(
          "Linkage Checker found errors in configurations. See above for the details.");
    }
  }
}
