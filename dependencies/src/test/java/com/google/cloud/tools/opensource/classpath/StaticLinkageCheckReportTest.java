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
import com.google.common.truth.Truth;

import java.nio.file.Paths;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StaticLinkageCheckReportTest {
  
  private JarLinkageReport jarLinkageReport;
  private StaticLinkageCheckReport staticLinkageCheckReport;

  @Before
  public void createDummyJarLinkageReport() {

    ClassSymbolReference classSymbolReference =
        ClassSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setSourceClassName("ClassB")
            .build();
    StaticLinkageError<ClassSymbolReference> linkageErrorMissingClass =
        StaticLinkageError.errorMissingTargetClass(classSymbolReference);
    ImmutableList<StaticLinkageError<ClassSymbolReference>> missingClassErrors =
        ImmutableList.of(linkageErrorMissingClass);

    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassB")
            .build();
    StaticLinkageError<MethodSymbolReference> linkageErrorMissingMethod =
        StaticLinkageError.errorMissingMember(methodSymbolReference, null);
    ImmutableList<StaticLinkageError<MethodSymbolReference>> missingMethodErrors =
        ImmutableList.of(linkageErrorMissingMethod);

    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setTargetClassName("ClassC")
            .setFieldName("fieldX")
            .setSourceClassName("ClassD")
            .build();
    StaticLinkageError<FieldSymbolReference> linkageErrorMissingField =
        StaticLinkageError.errorMissingMember(fieldSymbolReference, null);
    ImmutableList<StaticLinkageError<FieldSymbolReference>> missingFieldErrors =
        ImmutableList.of(linkageErrorMissingField);

    jarLinkageReport =
        JarLinkageReport.builder()
            .setJarPath(Paths.get("a", "b", "c"))
            .setMissingClassErrors(missingClassErrors)
            .setMissingMethodErrors(missingMethodErrors)
            .setMissingFieldErrors(missingFieldErrors)
            .build();

    staticLinkageCheckReport =
        StaticLinkageCheckReport.create(ImmutableList.of(jarLinkageReport));
  }

  @Test
  public void testJarLinkageReportToString() {
    Truth.assertThat(staticLinkageCheckReport.toString()).startsWith("c (3 errors)");
  }
  
  @Test
  public void testToString() {
    Truth.assertThat(staticLinkageCheckReport.toString()).contains(jarLinkageReport.toString());
  }
  
  @Test
  public void testToStringNoErrors() {
    staticLinkageCheckReport = StaticLinkageCheckReport.create(Collections.emptyList());
    Assert.assertEquals("No static linkage errors\n", staticLinkageCheckReport.toString());
  }
  
  @Test
  public void testCreation() {
    Assert.assertEquals(1, staticLinkageCheckReport.getJarLinkageReports().size());
    Assert.assertEquals(jarLinkageReport, staticLinkageCheckReport.getJarLinkageReports().get(0));
    Assert.assertEquals(
        "ClassA",
        staticLinkageCheckReport
            .getJarLinkageReports()
            .get(0)
            .getMissingClassErrors()
            .get(0)
            .getReference()
            .getTargetClassName());
  }
}
