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
import com.google.cloud.tools.opensource.classpath.LinkageProblem;
import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.cloud.tools.opensource.dependencies.MavenRepositoryException;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.eclipse.aether.RepositoryException;
import org.junit.Assert;
import org.junit.Test;

public class MaximumLinkageErrorsTest {

  @Test
  public void testForNewLinkageErrors()
      throws IOException, MavenRepositoryException, RepositoryException {
    // Not using RepositoryUtility.findLatestCoordinates, which may return a snapshot version
    String version = findLatestNonSnapshotVersion();
    String baselineCoordinates = "com.google.cloud:libraries-bom:" + version;
    Bom baseline = Bom.readBom(baselineCoordinates);

    Path bomFile = Paths.get("../cloud-oss-bom/pom.xml");
    Bom bom = Bom.readBom(bomFile);

    ImmutableSet<LinkageProblem> oldProblems =
        LinkageChecker.create(baseline).findSymbolProblems();
    LinkageChecker checker = LinkageChecker.create(bom);
    ImmutableSet<LinkageProblem> currentProblems = checker.findSymbolProblems();

    // This only tests for newly missing methods, not new references to
    // previously missing methods.
    SetView<LinkageProblem> newProblems =
        Sets.difference(currentProblems, oldProblems);

    // Check that no new linkage errors have been introduced since the baseline
    StringBuilder message = new StringBuilder("Baseline BOM: " + baselineCoordinates + "\n");
    if (!newProblems.isEmpty()) {
      message.append("Newly introduced problems:\n");
      message.append(LinkageProblem.formatLinkageProblems(newProblems));
      Assert.fail(message.toString());
    }
  }

  private String findLatestNonSnapshotVersion() throws MavenRepositoryException {
    ImmutableList<String> versions =
        RepositoryUtility.findVersions(
            RepositoryUtility.newRepositorySystem(), "com.google.cloud", "libraries-bom");
    ImmutableList<String> versionsLatestFirst = versions.reverse();
    Optional<String> highestNonsnapshotVersion =
        versionsLatestFirst.stream().filter(version -> !version.contains("SNAPSHOT")).findFirst();
    if (!highestNonsnapshotVersion.isPresent()) {
      Assert.fail("Could not find non-snapshot version of the BOM");
    }
    return highestNonsnapshotVersion.get();
  }
}
