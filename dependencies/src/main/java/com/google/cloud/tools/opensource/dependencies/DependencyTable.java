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

package com.google.cloud.tools.opensource.dependencies;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.cloud.tools.opensource.classpath.ClassPathBuilder;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import java.util.List;
import org.apache.commons.cli.ParseException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Tool to show a table of dependencies of Maven artifacts and BOMs.
 *
 * <ul>
 *   <li>Columns: Maven artifacts and BOMs specified in input</li>
 *   <li>Rows: the groupID and artifactID of the dependencies</li>
 *   <li>Cells: the version of corresponding dependencies for the column</li>
 * </ul>
 */
final class DependencyTable {

  public static void main(String[] args) throws ParseException, RepositoryException {
    DependencyTableArguments arguments = DependencyTableArguments.readCommandLine(args);

    ImmutableList<String> artifactCoordinates = arguments.getArtifactCoordinates();

    ImmutableList<String> bomCoordinates = arguments.getBomCoordinates();

    ImmutableSet.Builder<String> artifactKeys = ImmutableSet.builder();

    // RowKey: GroupID and ArtifactID of a dependency
    // ColumnKey: Artifact or BOM coordinates
    // Value: version
    Table<String, String, String> dependencyTable = HashBasedTable.create();
    for (String coordinates : artifactCoordinates) {
      DefaultArtifact rootArtifact = new DefaultArtifact(coordinates);
      List<DependencyPath> dependencyPaths =
          ClassPathBuilder.artifactsToDependencyPaths(ImmutableList.of(rootArtifact)).values();
      ImmutableList<Artifact> dependencies =
          dependencyPaths.stream().map(DependencyPath::getLeaf).collect(toImmutableList());

      dependencies.forEach(
          artifact -> {
            String row = Artifacts.makeKey(artifact);
            artifactKeys.add(row);
            dependencyTable.put(row, coordinates, artifact.getVersion());
          });
    }

    for (String coordinates : bomCoordinates) {
      ImmutableList<Artifact> managedDependencies =
          RepositoryUtility.readBom(coordinates).getManagedDependencies();

      managedDependencies.forEach(
          artifact -> {
            String row = Artifacts.makeKey(artifact);
            artifactKeys.add(row);
            dependencyTable.put(row, coordinates, artifact.getVersion());
          });
    }

    List<String> sortedRowKeys = Ordering.natural().sortedCopy(artifactKeys.build());

    System.out.println(
        formatAsJira(
            dependencyTable, sortedRowKeys, Iterables.concat(artifactCoordinates, bomCoordinates)));
  }

  static String formatAsJira(
      Table<String, String, String> table, Iterable<String> rowKeys, Iterable<String> columnKeys) {
    StringBuilder output = new StringBuilder();
    // Column headers
    output.append("|| ||"); // The first upper-right cell is empty
    for (String columnKey : columnKeys) {
      output.append(columnKey).append("||");
    }
    output.append("\n");

    for (String rowKey : rowKeys) {
      output.append('|').append(rowKey).append('|');
      for (String coordinates : columnKeys) {
        String version = table.get(rowKey, coordinates);
        output.append(version != null ? version : " ").append('|');
      }
      output.append("\n");
    }

    return output.toString();
  }
}
