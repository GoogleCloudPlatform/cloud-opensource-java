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
  public void testCreation() {
    ImmutableList<LinkageErrorMissingClass> linkageErrorMissingClasses =
        ImmutableList.of(LinkageErrorMissingClass.create("ClassA", "ClassB"));
    LinkageErrorMissingMethod linkageErrorMissingMethod =
        LinkageErrorMissingMethod.builder()
            .setTargetClassName("ClassA")
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassB")
            .build();
    JarLinkageReport jarLinkageReport =
        JarLinkageReport.builder()
            .setJarPath(Paths.get("a", "b", "c"))
            .setMissingClassErrors(linkageErrorMissingClasses)
            .setMissingMethodErrors(ImmutableList.of(linkageErrorMissingMethod))
            .setMissingFieldErrors(
                ImmutableList.of(LinkageErrorMissingField.create("ClassC", "fieldX", "ClassD")))
            .build();
    StaticLinkageCheckReport staticLinkageCheckReport =
        StaticLinkageCheckReport.create(ImmutableList.of(jarLinkageReport));

    Assert.assertEquals(jarLinkageReport, staticLinkageCheckReport.getJarLinkageReports().get(0));
    Assert.assertEquals(
        "ClassA",
        staticLinkageCheckReport
            .getJarLinkageReports()
            .get(0)
            .getMissingClassErrors()
            .get(0)
            .getTargetClassName());
  }
}
