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

import com.google.cloud.tools.opensource.classpath.ClassFile;
import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.cloud.tools.opensource.classpath.SymbolProblem;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.aether.RepositoryException;
import org.junit.Assert;
import org.junit.Test;

public class MaximumLinkageErrorsTest {

  @Test
  public void textMaximumLinkageErrors() 
      throws IOException, MavenRepositoryException, RepositoryException {

    Bom baseline = RepositoryUtility.readBom("com.google.cloud:libraries-bom:1.2.0");

    Path bomFile = Paths.get("../cloud-oss-bom/pom.xml");
    Bom bom = RepositoryUtility.readBom(bomFile);

    ImmutableSetMultimap<SymbolProblem, ClassFile> oldProblems =
        LinkageChecker.create(baseline).findSymbolProblems();
    ImmutableSetMultimap<SymbolProblem, ClassFile> newProblems =
        LinkageChecker.create(bom).findSymbolProblems();

    Assert.assertTrue("New linkage errors introduced", newProblems.keys().size() <= 525);
    
    // Check that no new linkage errors have been introduced since 1.2.0
    Truth.assertThat(oldProblems).containsAtLeastEntriesIn(newProblems);
  }
}
