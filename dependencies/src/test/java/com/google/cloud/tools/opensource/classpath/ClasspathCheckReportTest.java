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

public class ClasspathCheckReportTest {
  
  private JarLinkageReport jarLinkageReport;
  private ClasspathCheckReport classpathCheckReport;

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

    classpathCheckReport =
        ClasspathCheckReport.create(ImmutableList.of(jarLinkageReport));
  }

  @Test
  public void testJarLinkageReportToString() {
    Truth.assertThat(classpathCheckReport.toString()).startsWith("c (3 errors)");
  }
  
  @Test
  public void testToString() {
    Truth.assertThat(classpathCheckReport.toString()).contains(jarLinkageReport.toString());
  }
  
  @Test
  public void testToStringNoErrors() {
    classpathCheckReport = ClasspathCheckReport.create(Collections.emptyList());
    Assert.assertEquals("No static linkage errors\n", classpathCheckReport.toString());
  }
  
  @Test
  public void testCreation() {
    Assert.assertEquals(1, classpathCheckReport.getJarLinkageReports().size());
    Assert.assertEquals(jarLinkageReport, classpathCheckReport.getJarLinkageReports().get(0));
    Assert.assertEquals(
        "ClassA",
        classpathCheckReport
            .getJarLinkageReports()
            .get(0)
            .getMissingClassErrors()
            .get(0)
            .getReference()
            .getTargetClassName());
  }
}
