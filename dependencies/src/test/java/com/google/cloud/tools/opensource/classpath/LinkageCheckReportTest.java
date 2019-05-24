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

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
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
        LinkageCheckReport.create(ImmutableList.of(jarLinkageReport));
  }

  @Test
  public void testGetErrorString() {
    Truth.assertThat(linkageCheckReport.getErrorString()).startsWith("c (3 errors)");
  }
  
  @Test
  public void testGetErrorString_contents() {
    Truth.assertThat(linkageCheckReport.getErrorString()).contains(jarLinkageReport.getErrorString());
  }
  
  @Test
  public void testGetErrorString_noErrors() {
    linkageCheckReport = LinkageCheckReport.create(Collections.emptyList());
    Assert.assertEquals("No linkage errors\n", linkageCheckReport.getErrorString());
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

  @Test
  public void testConversion() throws IOException, URISyntaxException {
    Path grpcPath =
        absolutePathOfResource("testdata/grpc-google-cloud-firestore-v1beta1-0.28.0.jar");
    Path firestorePath = absolutePathOfResource("testdata/google-cloud-firestore-0.65.0-beta.jar");
    SymbolProblem symbolProblem1 =
        new SymbolProblem(
            new ClassSymbol("java.lang.Integer"),
            ErrorType.CLASS_NOT_FOUND,
            new ClassFile(Paths.get("foo", "bar.jar"), "java.lang.Object"));
    SymbolProblem symbolProblem2 =
        new SymbolProblem(
            new MethodSymbol("java.lang.Integer", "toString", "Ljava.lang.String;", false),
            ErrorType.INACCESSIBLE_MEMBER,
            new ClassFile(firestorePath, "java.lang.Object"));
    ImmutableSetMultimap<ClassFile, SymbolProblem> symbolProblems =
        ImmutableSetMultimap.of(
            new ClassFile(grpcPath, "com.google.firestore.v1beta1.FirestoreGrpc"), symbolProblem1,
            new ClassFile(firestorePath, "com.google.ClassB"), symbolProblem2);

    SymbolReferenceMaps.Builder referenceMapBuilder = new SymbolReferenceMaps.Builder();
    LinkageCheckReport linkageCheckReport =
        LinkageCheckReport.fromSymbolProblems(
            symbolProblems,
            ImmutableList.of(grpcPath, firestorePath),
            ClassReferenceGraph.create(referenceMapBuilder.build(), ImmutableSet.of(grpcPath)));
    Truth.assertThat(linkageCheckReport.getJarLinkageReports()).hasSize(2);
    JarLinkageReport path1Report = linkageCheckReport.getJarLinkageReports().get(0);
    Truth.assertThat(path1Report.getMissingClassErrors()).hasSize(1);
    SymbolNotResolvable<ClassSymbolReference> classSymbolNotResolvable =
        path1Report.getMissingClassErrors().get(0);
    ClassSymbolReference missingReference = classSymbolNotResolvable.getReference();
    Truth.assertThat(missingReference.getTargetClassName()).isEqualTo("java.lang.Integer");
    Truth.assertWithMessage("The source class 'FirestoreGrpc' is in entry point jar")
        .that(classSymbolNotResolvable.isReachable())
        .isTrue();

    JarLinkageReport path2Report = linkageCheckReport.getJarLinkageReports().get(1);
    Truth.assertThat(path2Report.getMissingMethodErrors()).hasSize(1);
    SymbolNotResolvable<MethodSymbolReference> methodSymbolUnresolvable =
        path2Report.getMissingMethodErrors().get(0);
    Truth.assertThat(methodSymbolUnresolvable.getReference().getMethodName()).isEqualTo("toString");
    Truth.assertThat(methodSymbolUnresolvable.getReason())
        .isSameInstanceAs(ErrorType.INACCESSIBLE_MEMBER);
    Truth.assertWithMessage("The source class 'ClassB' should not be reachable")
        .that(methodSymbolUnresolvable.isReachable())
        .isFalse();
  }
}
