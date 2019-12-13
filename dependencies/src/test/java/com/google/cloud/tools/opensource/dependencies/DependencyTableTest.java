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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

public class DependencyTableTest {

  @Test
  public void testFormatTableInJira() throws ParseException {
    // JIRA table formatting guide:
    // https://issues.apache.org/jira/secure/WikiRendererHelpAction.jspa?section=tables

    String expected =
        "|| ||col1||col2||\n"
            + "|row1|V1|V2|\n"
            + "|row2|V3| |\n"; // The top-left and botton right are empty.

    Table<String, String, String> table = HashBasedTable.create();
    table.put("row1", "col1", "V1");
    table.put("row1", "col2", "V2");
    table.put("row2", "col1", "V3");

    String formatted =
        DependencyTable.formatAsJira(
            table, ImmutableList.of("row1", "row2"), ImmutableList.of("col1", "col2"));
    assertEquals(expected, formatted);
  }
}
