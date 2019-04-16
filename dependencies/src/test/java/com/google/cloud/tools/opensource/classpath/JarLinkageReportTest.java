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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JarLinkageReportTest {

  private JarLinkageReport jarLinkageReport;
  private ImmutableList<SymbolNotResolvable<FieldSymbolReference>> missingFieldErrors;
  private ImmutableList<SymbolNotResolvable<MethodSymbolReference>> missingMethodErrors;
  private ImmutableList<SymbolNotResolvable<ClassSymbolReference>> missingClassErrors;
  private SymbolNotResolvable<MethodSymbolReference> linkageErrorMissingMethod;

  @Before
  public void setUp() {

    ClassSymbolReference classSymbolReference =
        ClassSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setSubclass(false)
            .setSourceClassName("ClassB")
            .build();

    SymbolNotResolvable<ClassSymbolReference> linkageErrorMissingClass =
        SymbolNotResolvable.errorMissingTargetClass(classSymbolReference, true);
    missingClassErrors = ImmutableList.of(linkageErrorMissingClass);

    MethodSymbolReference methodSymbolReference =
        MethodSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setInterfaceMethod(false)
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassB")
            .build();
    Path targetClassLocation = Paths.get("dummy.jar");
    linkageErrorMissingMethod =
        SymbolNotResolvable.errorMissingMember(methodSymbolReference, targetClassLocation, true);

    MethodSymbolReference methodSymbolReferenceDueToMissingClass =
        MethodSymbolReference.builder()
            .setTargetClassName("ClassA")
            .setInterfaceMethod(false)
            .setMethodName("methodX")
            .setDescriptor("java.lang.String")
            .setSourceClassName("ClassC$InnerC")
            .build();
    SymbolNotResolvable<MethodSymbolReference> linkageErrorMissingMethodByClass =
        SymbolNotResolvable.errorMissingTargetClass(methodSymbolReferenceDueToMissingClass, false);

    missingMethodErrors =
        ImmutableList.of(linkageErrorMissingMethod, linkageErrorMissingMethodByClass);

    FieldSymbolReference fieldSymbolReference =
        FieldSymbolReference.builder()
            .setTargetClassName("ClassC")
            .setFieldName("fieldX")
            .setSourceClassName("ClassD")
            .build();
    SymbolNotResolvable<FieldSymbolReference> linkageErrorMissingField =
        SymbolNotResolvable.errorMissingTargetClass(fieldSymbolReference, true);
    missingFieldErrors = ImmutableList.of(linkageErrorMissingField);
    jarLinkageReport =
        JarLinkageReport.builder()
            .setJarPath(Paths.get("a", "b", "c"))
            .setMissingClassErrors(missingClassErrors)
            .setMissingMethodErrors(missingMethodErrors)
            .setMissingFieldErrors(missingFieldErrors)
            .build();
  }

  @Test
  public void testGetJarPath() {
    Assert.assertEquals(Paths.get("a", "b", "c"), jarLinkageReport.getJarPath());
  }

  @Test
  public void testGetMissingMethodErrors() {
    Assert.assertEquals(missingMethodErrors, jarLinkageReport.getMissingMethodErrors());
  }

  @Test
  public void testGetMissingFieldErrors() {
    Assert.assertEquals(missingFieldErrors, jarLinkageReport.getMissingFieldErrors());
  }

  @Test
  public void testGetMissingClassErrors() {
    Assert.assertEquals(missingClassErrors, jarLinkageReport.getMissingClassErrors());
  }

  @Test
  public void testGetTotalErrorCount() {
    Assert.assertEquals(4, jarLinkageReport.getErrorCount());
  }

  @Test
  public void testToString() {
    Assert.assertEquals(
        "c (4 errors):\n"
            + "  ClassSymbolReference{sourceClassName=ClassB, targetClassName=ClassA"
            + ", subclass=false}, reason: CLASS_NOT_FOUND, target class location not found"
            + ", isReachable: true\n"
            + "  MethodSymbolReference{sourceClassName=ClassB, targetClassName=ClassA, "
            + "methodName=methodX, interfaceMethod=false, descriptor=java.lang.String}"
            + ", reason: SYMBOL_NOT_FOUND, target class from dummy.jar"
            + ", isReachable: true\n"
            + "  MethodSymbolReference{sourceClassName=ClassC$InnerC, targetClassName=ClassA,"
            + " methodName=methodX, interfaceMethod=false, descriptor=java.lang.String}, "
            + "reason: CLASS_NOT_FOUND, target class location not found"
            + ", isReachable: false\n"
            + "  FieldSymbolReference{sourceClassName=ClassD, targetClassName=ClassC, "
            + "fieldName=fieldX}, reason: CLASS_NOT_FOUND, target class location not found"
            + ", isReachable: true\n",
        jarLinkageReport.toString());
  }

  @Test
  public void testGetErrorString() {
    Assert.assertEquals(
        "c (4 errors):\n"
            + "  ClassA is not found, referenced from ClassB\n"
            + "  ClassA.methodX is not found, referenced from ClassB\n"
            + "  ClassA.methodX is not found, referenced from ClassC$InnerC\n"
            + "  ClassC.fieldX is not found, referenced from ClassD\n",
        jarLinkageReport.getErrorString());
  }  
  
  @Test
  public void testGetCauseToSourceClasses() {
    ImmutableMultimap<LinkageErrorCause, String> causeToSourceClasses =
        jarLinkageReport.getCauseToSourceClasses();

    ImmutableSet<LinkageErrorCause> linkageErrorCauses = causeToSourceClasses.keySet();
    Truth.assertThat(linkageErrorCauses).hasSize(3);
    ImmutableCollection<String> classesForFirstCause =
        causeToSourceClasses.get(linkageErrorCauses.iterator().next());
    // InnerC should not appear here
    Truth.assertThat(classesForFirstCause).containsExactly("ClassB", "ClassC");
  }
}
