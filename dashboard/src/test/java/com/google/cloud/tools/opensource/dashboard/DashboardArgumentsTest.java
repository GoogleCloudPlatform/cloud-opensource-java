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

package com.google.cloud.tools.opensource.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import org.apache.commons.cli.AlreadySelectedException;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.ParseException;
import org.junit.Assert;
import org.junit.Test;

public class DashboardArgumentsTest {

  @Test
  public void testParseArgument_file() throws ParseException {
    DashboardArguments dashboardArguments = DashboardArguments.readCommandLine("-f", "../pom.xml");

    assertTrue(dashboardArguments.hasFile());
    assertEquals(Paths.get("../pom.xml").toAbsolutePath(), dashboardArguments.getBomFile());
  }

  @Test
  public void testParseArgument_coordinates() throws ParseException {
    DashboardArguments dashboardArguments =
        DashboardArguments.readCommandLine("-c", "com.google.cloud:bom:1.0.0-SNAPSHOT");
    assertFalse(dashboardArguments.hasFile());
    assertEquals(
        "com.google.cloud:bom:1.0.0-SNAPSHOT", dashboardArguments.getBomCoordinates());
  }

  @Test
  public void testParseArgument_missingInput() throws ParseException {
    try {
      DashboardArguments.readCommandLine();
      Assert.fail("The argument should validate missing input");
    } catch (MissingOptionException ex) {
      // pass
    }
  }

  @Test
  public void testParseArgument_duplicateOptions() throws ParseException {
    try {
      DashboardArguments.readCommandLine(
          "-c", "com.google.cloud:bom:1.0.0-SNAPSHOT", "-f", "../pom.xml");
      Assert.fail("The argument should validate duplicate input");
    } catch (AlreadySelectedException ex) {
      // pass
    }
  }
}
