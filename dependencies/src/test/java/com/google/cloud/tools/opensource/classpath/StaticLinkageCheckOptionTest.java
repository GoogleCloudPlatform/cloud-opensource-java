/*
 * Copyright 2018 Google LLC.
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
import java.nio.file.Paths;
import org.apache.commons.cli.ParseException;
import org.junit.Assert;
import org.junit.Test;

public class StaticLinkageCheckOptionTest {
  @Test
  public void parseCommandLineOptions_shortOptions_bom() throws ParseException {
    String[] arguments = new String[] {
        "-b", "abc.com:dummy:1.2",
        "-r"
    };
    StaticLinkageCheckOption parsedOption = StaticLinkageCheckOption.parseArguments(arguments);

    Assert.assertEquals("abc.com:dummy:1.2", parsedOption.getBomCoordinate());
    Assert.assertTrue(parsedOption.isReportOnlyReachable());
  }

  @Test
  public void parseCommandLineOptions_longOptions() throws ParseException {
    String[] arguments = new String[] {
        "--coordinate", "abc.com:abc:1.1,abc.com:abc-util:1.2",
        "--report-only-reachable"
    };
    StaticLinkageCheckOption parsedOption = StaticLinkageCheckOption.parseArguments(arguments);

    Assert.assertEquals(ImmutableList.of("abc.com:abc:1.1", "abc.com:abc-util:1.2"),
        parsedOption.getMavenCoordinates());
    Assert.assertTrue(parsedOption.isReportOnlyReachable());
  }

  @Test
  public void parseCommandLineOptions_emptyOption() throws ParseException {
    StaticLinkageCheckOption parsedOption = StaticLinkageCheckOption.parseArguments(new String[0]);

    Assert.assertTrue(parsedOption.getJarFileList().isEmpty());
    Assert.assertNull(parsedOption.getBomCoordinate());
    Assert.assertTrue(parsedOption.getMavenCoordinates().isEmpty());
    Assert.assertFalse(parsedOption.isReportOnlyReachable());
  }

  @Test
  public void parseCommandLineOptions_invalidOption() {
    String[] arguments = new String[] {
        "-x" // No such option
    };
    try {
      StaticLinkageCheckOption.parseArguments(arguments);
      Assert.fail();
    } catch (ParseException ex) {
      Assert.assertEquals("Unrecognized option: -x", ex.getMessage());
    }
  }

}
