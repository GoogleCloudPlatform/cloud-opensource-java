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
import org.junit.Assert;
import org.junit.Test;

public class StaticLinkageCheckReportTest {

  @Test
  public void testStaticLinkageCheckReportInstantiation() {
    ImmutableList<MissingClassReport> missingClassReports =
        ImmutableList.of(MissingClassReport.create("ClassA", "ClassB"));
    JarLinkageReport jarLinkageReport =
        JarLinkageReport.create(Paths.get("a", "b", "c"), missingClassReports);
    StaticLinkageCheckReport staticLinkageCheckReport =
        StaticLinkageCheckReport.create(ImmutableList.of(jarLinkageReport));

    Assert.assertEquals(
        staticLinkageCheckReport.jarLinkageReports().get(0).jarPath(), Paths.get("a", "b", "c"));
    Assert.assertEquals(
        "ClassA",
        staticLinkageCheckReport
            .jarLinkageReports()
            .get(0)
            .missingClassReports()
            .get(0)
            .missingClassName());
  }

  @Test
  public void testMissingMethodReport_builder() {
    MissingMethodReport missingMethodReport =
        MissingMethodReport.builder()
            .setClassName("ClassA")
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassB")
            .build();

    Assert.assertEquals("ClassB", missingMethodReport.sourceClassName());
  }

}
