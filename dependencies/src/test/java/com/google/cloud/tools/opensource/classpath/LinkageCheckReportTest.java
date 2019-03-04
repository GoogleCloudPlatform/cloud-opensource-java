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
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LinkageCheckReportTest {
  
  private JarLinkageReport jarLinkageReport;
  private LinkageCheckReport linkageCheckReport;

  @Before
  public void createDummyJarLinkageReport() {

    ClassSymbolReference classSymbolReference =
        ClassSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setSubclass(false)
            .setSourceClassName("ClassB")
            .build();
    SymbolNotResolvable<ClassSymbolReference> linkageErrorMissingClass =
        SymbolNotResolvable.errorMissingTargetClass(classSymbolReference, true);
    ImmutableList<SymbolNotResolvable<ClassSymbolReference>> missingClassErrors =
        ImmutableList.of(linkageErrorMissingClass);

    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setInterfaceMethod(false)
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassB")
            .build();
    SymbolNotResolvable<MethodSymbolReference> linkageErrorMissingMethod =
        SymbolNotResolvable.errorMissingMember(methodSymbolReference, null, true);
    ImmutableList<SymbolNotResolvable<MethodSymbolReference>> missingMethodErrors =
        ImmutableList.of(linkageErrorMissingMethod);

    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setTargetClassName("ClassC")
            .setFieldName("fieldX")
            .setSourceClassName("ClassD")
            .build();
    SymbolNotResolvable<FieldSymbolReference> linkageErrorMissingField =
        SymbolNotResolvable.errorMissingMember(fieldSymbolReference, null, true);
    ImmutableList<SymbolNotResolvable<FieldSymbolReference>> missingFieldErrors =
        ImmutableList.of(linkageErrorMissingField);

    jarLinkageReport =
        JarLinkageReport.builder()
            .setJarPath(Paths.get("a", "b", "c"))
            .setMissingClassErrors(missingClassErrors)
            .setMissingMethodErrors(missingMethodErrors)
            .setMissingFieldErrors(missingFieldErrors)
            .build();

    linkageCheckReport =
        LinkageCheckReport.create(ImmutableList.of(jarLinkageReport), ImmutableMap.of());
  }

  @Test
  public void testJarLinkageReportToString() {
    Truth.assertThat(linkageCheckReport.toString()).startsWith("c (3 errors)");
  }
  
  @Test
  public void testToString() {
    Truth.assertThat(linkageCheckReport.toString()).contains(jarLinkageReport.toString());
  }
  
  @Test
  public void testToStringNoErrors() {
    linkageCheckReport = LinkageCheckReport.create(Collections.emptyList(), ImmutableMap.of());
    Assert.assertEquals("No linkage errors\n", linkageCheckReport.toString());
  }
  
  @Test
  public void testCreation() {
    Assert.assertEquals(1, linkageCheckReport.getJarLinkageReports().size());
    Assert.assertEquals(jarLinkageReport, linkageCheckReport.getJarLinkageReports().get(0));
    Assert.assertEquals(
        "ClassA",
        linkageCheckReport
            .getJarLinkageReports()
            .get(0)
            .getMissingClassErrors()
            .get(0)
            .getReference()
            .getTargetClassName());
  }
}
