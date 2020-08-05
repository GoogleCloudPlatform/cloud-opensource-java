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

package com.google.cloud.tools.opensource.classpath;

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import org.apache.bcel.classfile.JavaClass;
import org.eclipse.aether.artifact.Artifact;

class SplitPackageFinder {

  public static void main(String[] arguments)
      throws MavenRepositoryException, IOException, ClassNotFoundException {

    Path bomFile = Paths.get(arguments[0]);
    Bom bom = Bom.readBom(bomFile);

    ClassPathBuilder classPathBuilder = new ClassPathBuilder();
    ClassPathResult resolved = classPathBuilder.resolve(bom.getManagedDependencies(), false);

    ImmutableSetMultimap.Builder<String, Artifact> packageToArtifactsBuilder = ImmutableSetMultimap.builder();
    for (ClassPathEntry entry : resolved.getClassPath()) {
      Artifact artifact = entry.getArtifact();
      ClassDumper classDumper = ClassDumper
          .create(ImmutableList.of(entry));

      for (String fileName : entry.getFileNames()) {
        JavaClass javaClass = classDumper.loadJavaClass(fileName);
        String packageName = javaClass.getPackageName();
        if (!packageName.isEmpty()) {
          packageToArtifactsBuilder.put(packageName, artifact);
        }
      }
    }

    ImmutableSetMultimap<String, Artifact> packageToArtifacts = packageToArtifactsBuilder.build();
    for (String packageName : packageToArtifacts.keySet()) {
      ImmutableSet<Artifact> artifacts = packageToArtifacts.get(packageName);
      if (artifacts.size() == 1) {
        continue;
      }
      System.out.println("Package: " + packageName);
      for (Artifact artifact : artifacts) {
        System.out.println("  artifact: " + artifact);
      }
    }
  }
}
