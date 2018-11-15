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

public class JarLinkageReportTest {

  @Test
  public void testCreation() {
    ClassSymbolReference classSymbolReference =
        ClassSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setSourceClassName("ClassB")
            .build();
    LinkageErrorMissingClass linkageErrorMissingClass =
        LinkageErrorMissingClass.builder().setReference(classSymbolReference).build();

    ImmutableList<LinkageErrorMissingClass> linkageErrorMissingClasses =
        ImmutableList.of(linkageErrorMissingClass);

    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassB")
            .build();
    LinkageErrorMissingMethod linkageErrorMissingMethod =
        LinkageErrorMissingMethod.builder()
            .setReference(methodSymbolReference)
            .build();
    ImmutableList<LinkageErrorMissingMethod> missingMethodErrors =
        ImmutableList.of(linkageErrorMissingMethod);

    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setTargetClassName("ClassC")
            .setFieldName("fieldX")
            .setSourceClassName("ClassD")
            .build();
    LinkageErrorMissingField linkageErrorMissingField =
        LinkageErrorMissingField.builder()
            .setReference(fieldSymbolReference)
            .build();
    ImmutableList<LinkageErrorMissingField> missingFieldErrors =
        ImmutableList.of(linkageErrorMissingField);
    JarLinkageReport jarLinkageReport =
        JarLinkageReport.builder()
            .setJarPath(Paths.get("a", "b", "c"))
            .setMissingClassErrors(linkageErrorMissingClasses)
            .setMissingMethodErrors(missingMethodErrors)
            .setMissingFieldErrors(missingFieldErrors)
            .build();

    Assert.assertEquals(Paths.get("a", "b", "c"), jarLinkageReport.getJarPath());
    Assert.assertEquals(missingMethodErrors, jarLinkageReport.getMissingMethodErrors());
    Assert.assertEquals(missingFieldErrors, jarLinkageReport.getMissingFieldErrors());
    Assert.assertEquals(linkageErrorMissingClasses, jarLinkageReport.getMissingClassErrors());
  }
}
