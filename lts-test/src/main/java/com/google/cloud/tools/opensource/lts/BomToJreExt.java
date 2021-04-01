/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.opensource.lts;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.ClassPathEntry;
import com.google.cloud.tools.opensource.classpath.ClassPathResult;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import org.eclipse.aether.artifact.Artifact;

public class BomToJreExt {
  private static final Logger logger = Logger.getLogger(BomToJreExt.class.getName());

  public static void main(String[] arguments) throws MavenRepositoryException, IOException {

    logger.info("Reading BOM");

    Path bomFile = Paths.get("boms", "cloud-lts-bom", "pom.xml");
    Bom bom = Bom.readBom(bomFile);
    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ImmutableList<Artifact> bomManagedDependencies = bom.getManagedDependencies();
    ClassPathResult resolvedDependencies = classPathBuilder.resolve(bomManagedDependencies, false);
    ImmutableList<ClassPathEntry> resolvedManagedDependencies =
        resolvedDependencies.getClassPath().subList(0, bomManagedDependencies.size());

    String javaHome = System.getProperty("java.home");

    logger.info("Copying managed dependencies to " + javaHome);

    Path extDirectory = Paths.get(javaHome).resolve("lib").resolve("ext");
    for (ClassPathEntry resolvedManagedDependency : resolvedManagedDependencies) {
      Artifact artifact = resolvedManagedDependency.getArtifact();
      if ("guava".equals(artifact.getArtifactId())) {
        // Placing Guava in the ext directory stops Maven
        continue;
      }
      Path jarInBom = artifact.getFile().toPath();
      Files.copy(jarInBom, extDirectory.resolve(jarInBom.getFileName()),
          REPLACE_EXISTING,
          COPY_ATTRIBUTES);
    }
    logger.info("Copied " + resolvedManagedDependencies.size() + " files");
  }
}
