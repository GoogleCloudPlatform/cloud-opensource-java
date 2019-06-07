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


package com.google.cloud;

import static org.junit.Assert.assertTrue;

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedListMultimap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.junit.Assert;
import org.junit.Test;

public class MaximumLinkageErrorsTest {

  @Test
  public void textMaximumLinkageErrors() 
      throws PlexusContainerException, ComponentLookupException, ProjectBuildingException, 
             RepositoryException, IOException {
    Path bomFile = Paths.get("../cloud-oss-bom/pom.xml");
    Bom bom = RepositoryUtility.readBom(bomFile);
    
    // duplicate code from DashboardMain follows. We need to refactor to extract this.
    ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();

    LinkedListMultimap<Path, DependencyPath> jarToDependencyPaths =
        ClassPathBuilder.artifactsToDependencyPaths(managedDependencies);
    // LinkedListMultimap preserves the key order
    ImmutableList<Path> classpath = ImmutableList.copyOf(jarToDependencyPaths.keySet());

    // When checking a BOM, entry point classes are the ones in the artifacts listed in the BOM
    List<Path> artifactJarsInBom = classpath.subList(0, managedDependencies.size());
    ImmutableSet<Path> entryPoints = ImmutableSet.copyOf(artifactJarsInBom);

    LinkageChecker linkageChecker = LinkageChecker.create(classpath, entryPoints);

    ImmutableSetMultimap<SymbolProblem, ClassFile> symbolProblems =
        linkageChecker.findSymbolProblems();

    Assert.assertTrue("New linkage errors introduced", symbolProblems.keys().size() <= 525);
    
    // If this next line fails, then the situation has actually improved and we should update
    // the test to match. 
    Assert.assertEquals("Total linkage errors reduced; update test", 516, symbolProblems.keys().size());
  }
}
