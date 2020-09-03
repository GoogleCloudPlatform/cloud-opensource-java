/*
 * Copyright 2020 Google LLC.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class ExcludedErrorsTest {
  @Test
  public void testDefaultRules_contains() throws IOException {
    ExcludedErrors excludedErrors = ExcludedErrors.create((Path) null);

    String targetClassName = "jdk.vm.ci.Bar";
    MethodSymbol methodSymbol =
        new MethodSymbol(targetClassName, "equals", "(Ljava/lang/Object;)Z", false);

    ClassFile sourceClass = new ClassFile(new ClassPathEntry(Paths.get("foo")), "org.graalvm.Foo");
    LinkageProblem linkageProblem =
        new AbstractMethodProblem(
            sourceClass,
            methodSymbol, new ClassFile(new ClassPathEntry(Paths.get("bar")), "org.graalvm.Bar")
        );
    assertTrue(excludedErrors.contains(linkageProblem));
  }

  @Test
  public void testDefaultRules_doesNotContains() throws IOException {
    ExcludedErrors excludedErrors = ExcludedErrors.create((Path) null);

    String targetClassName = "com.google.Bar";
    MethodSymbol methodSymbol =
        new MethodSymbol(targetClassName, "equals", "(Ljava/lang/Object;)Z", false);

    ClassFile sourceClass = new ClassFile(new ClassPathEntry(Paths.get("foo")), "org.graalvm.Foo");
    LinkageProblem linkageProblem =
        new AbstractMethodProblem(
            sourceClass,
            methodSymbol, new ClassFile(new ClassPathEntry(Paths.get("bar")), "org.graalvm.Bar")
        );

    assertFalse(excludedErrors.contains(linkageProblem));
  }
}
