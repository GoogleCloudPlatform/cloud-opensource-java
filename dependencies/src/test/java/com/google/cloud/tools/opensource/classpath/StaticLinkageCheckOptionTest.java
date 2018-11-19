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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.junit.Assert;
import org.junit.Test;

public class StaticLinkageCheckOptionTest {
  @Test
  public void parseCommandLineOptions_shortOptions_bom() throws ParseException {
    String[] arguments = {"-b", "abc.com:dummy:1.2", "-r"};
    CommandLine parsedOption = StaticLinkageCheckOption.readCommandLine(arguments);

    Assert.assertEquals("abc.com:dummy:1.2", parsedOption.getOptionObject('b'));
  }

  @Test
  public void parseCommandLineOptions_duplicates() {
    String[] arguments = {
        "--artifacts", "abc.com:abc:1.1,abc.com:abc-util:1.2",
        "-b", "abc.com:dummy:1.2",
        "--report-only-reachable"
    };
    try {
      StaticLinkageCheckOption.readCommandLine(arguments);
      Assert.fail();
    } catch (ParseException ex) {
      Assert.assertEquals(
          "Exactly one of BOM, Maven coordinates, or jar files must be specified",
          ex.getMessage());
    }
  }

  @Test
  public void parseCommandLineOptions_invalidOption() {
    String[] arguments = {"-x"}; // No such option 
    try {
      StaticLinkageCheckOption.readCommandLine(arguments);
      Assert.fail();
    } catch (ParseException ex) {
      Assert.assertEquals("Unrecognized option: -x", ex.getMessage());
    }
  }
}
