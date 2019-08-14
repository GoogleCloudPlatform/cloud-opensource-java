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

package com.google.cloud.tools.opensource.classpath;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import org.eclipse.aether.artifact.Artifact;

public class ModularityChecker {
  private ImmutableList<Path> jars;
  private ImmutableMap<Path, Artifact> artifacts;

  public ModularityChecker(Iterable<Path> jars, Iterable<Artifact> artifacts) {
    this.jars = ImmutableList.copyOf(jars);

    ImmutableMap.Builder<Path, Artifact> builder = ImmutableMap.builder();
    for (Artifact artifact: artifacts) {
      builder.put(artifact.getFile().toPath(), artifact);
    }
    this.artifacts = builder.build();
  }

  public void run() {
    ImmutableListMultimap.Builder<String, Path> builder = ImmutableListMultimap.builder();
    ImmutableList.Builder<Path> moduleInfoJars = ImmutableList.builder();
    ImmutableList.Builder<Path> nonAutomaticModuleNameJars = ImmutableList.builder();
    for (Path jar : jars) {

      Optional<String> moduleInfo = readModuleInfo(jar);
      if (moduleInfo.isPresent()) {
        moduleInfoJars.add(jar);
      }
      Optional<String> automaticModuleName = readAutomaticModuleName(jar);
      if (automaticModuleName.isPresent()) {
        builder.put(automaticModuleName.get(), jar);
      } else {
        nonAutomaticModuleNameJars.add(jar);
      }

    }
    ImmutableListMultimap<String, Path> moduleNameToJar = builder.build();

    System.out.println("Total Artifacts: " + jars.size());
    ImmutableList<Path> jarsWithoutAutomaticModuleName = nonAutomaticModuleNameJars.build();
    System.out.println("Artifacts with Automatic Module Name in Manifest: " + moduleNameToJar.values().stream().distinct().count());
    ImmutableList<Path> jarWithModuleInfo = moduleInfoJars.build();
    System.out.println("Artifacts with Module-Info: " + jarWithModuleInfo.size());

    for (String moduleName: moduleNameToJar.keySet()) {
      ImmutableList<Path> paths = moduleNameToJar.get(moduleName);
      System.out.println("Automatic Module Name: " + moduleName);
      if (paths.size() == 1) {
        System.out.println("  : " + artifacts.get(paths.get(0)));
        continue;
      }
      paths.forEach(path -> {
        System.out.println("  duplicate: " + artifacts.get(path));
      });
    }

    if (!jarWithModuleInfo.isEmpty()) {
      System.out.println("\nArtifacts including module-info: " + jarWithModuleInfo.size());
      jarWithModuleInfo.forEach(jar -> {
        System.out.println("  " + artifacts.get(jar));
      });
    }

    if (!jarsWithoutAutomaticModuleName.isEmpty()) {
      System.out.println("\nArtifacts without Automatic Module Name: " + jarsWithoutAutomaticModuleName.size());
      jarsWithoutAutomaticModuleName.forEach(jar -> {
        System.out.println("  " + artifacts.get(jar));
      });
    }
  }


  private static Optional<String> readAutomaticModuleName(Path jar) {
    try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jar.toFile()))) {
      Manifest manifest = jarStream.getManifest();
      String name = "Automatic-Module-Name";
      if (manifest == null) {
        return Optional.empty();
      }
      Attributes attributes = manifest.getMainAttributes();
      if (attributes == null || attributes.size() < 1) {
        return Optional.empty();
      }
      return Optional.ofNullable(attributes.getValue(name));
    } catch (IOException ex) {
      throw new RuntimeException("Could not open putstream", ex);
    }
  }

  private static Optional<String> readModuleInfo(Path jar) {
    /* This logic is tested by recent logr4j-api 2 that supports Java 9 module via multi-release JAR
      https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/339

      <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-api</artifactId>
        <version>2.12.1</version>
      </dependency>

     */
    try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jar.toFile()))) {
      for (JarEntry jarEntry = jarStream.getNextJarEntry(); jarEntry != null; jarEntry = jarStream.getNextJarEntry()) {
        String name = jarEntry.getName();
        if (name.toLowerCase().contains("module-info")) {
          System.out.println("Found module-info");
          return Optional.of(name);
        }
      }
      return Optional.empty();
    } catch (IOException ex) {
      throw new RuntimeException("Could not open putstream", ex);
    }
  }
}
