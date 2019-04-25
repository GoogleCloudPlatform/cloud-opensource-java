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

import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.cloud.tools.opensource.dependencies.RepositoryUtility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.List;
import java.util.Date;
import java.util.Map;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

/**
 * A tool to find linkage errors for a class path. This outputs comma-separated values for the
 * pair-wise comparison table.
 */
public class LeagueTableMain {

  // This values are used in resolving dependencies for a pair in the same manner as
  // user's pom.xml importing Cloud OSS BOM.
  public static final List<Dependency> managedDependencies = Lists.newArrayList();
  public static final Map<String, Artifact> managedDependencyMap = Maps.newHashMap();

  /** Outputs the pair-wise comparison table for the members of Cloud OSS BOM. */
  public static void main(String[] arguments)
      throws IOException, RepositoryException, ParseException {

    LinkageCheckerArguments linkageCheckerArguments =
        LinkageCheckerArguments.readCommandLine("-b", "com.google.cloud:cloud-oss-bom:1.0.0-SNAPSHOT");

    RepositoryUtility.setRepositories(
        linkageCheckerArguments.getExtraMavenRepositoryUrls(),
        linkageCheckerArguments.getAddMavenCentral());

    ImmutableList<Artifact> bomMembers = linkageCheckerArguments.getArtifacts();

    Map<String, LinkageCheckReport> table = Maps.newHashMap();

    StringBuilder cvsBuilder = new StringBuilder();

    // Header
    cvsBuilder.append("artifacts,");
    for (Artifact bomMember : bomMembers) {
      cvsBuilder.append(bomMember).append(",");
      managedDependencies.add(new Dependency(bomMember, "compile"));
      managedDependencyMap.put(Artifacts.makeKey(bomMember), bomMember);
    }

    cvsBuilder.append("\n");
    int cellTotal = bomMembers.size() * bomMembers.size();
    int cellCount = 0;
    long startTimeMillis = System.currentTimeMillis();
    for (Artifact bomMember1 : bomMembers) {
      cvsBuilder.append(bomMember1).append(",");
      for (Artifact bomMember2 : bomMembers) {
        cellCount++;
        String key = bomMember1.toString() + " " + bomMember2;


        long currentTimeMillis = System.currentTimeMillis();
        long diffMillis = currentTimeMillis - startTimeMillis;
        // cellCount is guaranteed to be non-zero
        long remainingSecs = diffMillis / cellCount * cellTotal / 1000;
        System.out.println(
            String.format(
                "Running %s (%d/%d). ETA: %d seconds", key, cellCount, cellTotal, remainingSecs));
        
        if (bomMember1.getVersion().equals(bomMember2.getVersion())) {
          cvsBuilder.append("-1").append(",");
          continue;
        }
        LinkageCheckerArguments argumentForPair =
            LinkageCheckerArguments.readCommandLine(
                "-a", bomMember1.toString() + "," + bomMember2.toString());

        LinkageChecker linkageChecker =
            LinkageChecker.create(
                argumentForPair.getInputClasspath(),
                argumentForPair.getEntryPointJars());
        LinkageCheckReport report = linkageChecker.findLinkageErrors();

        long problematicJarCount =
            report.getJarLinkageReports().stream()
                .filter(jarLinkageReport -> jarLinkageReport.getErrorCount() > 0)
                .count();
//        report.getJarLinkageReports().stream()
      //  .filter(jarLinkageReport -> jarLinkageReport.getErrorCount() > 0)
        //.forEach(r -> System.out.println(r));
        table.put(key, report);

        cvsBuilder.append(problematicJarCount).append(",");
      }
      cvsBuilder.append("\n");
    }
    System.out.println(cvsBuilder.toString());
    System.out.println(new Date() + ": Done");
  }
}
