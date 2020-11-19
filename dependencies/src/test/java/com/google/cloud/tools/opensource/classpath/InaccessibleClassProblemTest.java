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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class InaccessibleClassProblemTest {
  
  private ClassFile file;
  private ClassSymbol symbol;

  @Before
  public void setUp() {
    Path path = Paths.get("/usr/tmp");
    ClassPathEntry entry = new ClassPathEntry(path);
    file = new ClassFile(entry, "foo");
    symbol = new ClassSymbol("Foo");
  }

  @Test
  public void testProtected() {
    InaccessibleClassProblem problem =
        new InaccessibleClassProblem(file, file, symbol, AccessModifier.PROTECTED);
    Assert.assertEquals("Class Foo is protected and is referenced by foo", problem.toString());
  }

  @Test
  public void testPrivate() {
    InaccessibleClassProblem problem =
        new InaccessibleClassProblem(file, file, symbol, AccessModifier.PRIVATE);
    Assert.assertEquals("Class Foo is private and is referenced by foo", problem.toString());
  }

  @Test
  public void testPublic() {
    InaccessibleClassProblem problem =
        new InaccessibleClassProblem(file, file, symbol, AccessModifier.PUBLIC);
    Assert.assertEquals("Class Foo is public and is referenced by foo", problem.toString());
  }

  @Test
  public void testDefault() {
    InaccessibleClassProblem problem =
        new InaccessibleClassProblem(file, file, symbol, AccessModifier.DEFAULT);
    Assert.assertEquals(
        "Class Foo has default access and is referenced by foo (different package)",
        problem.toString());
  }

  @Test
  public void testToString_nullTargetClass() {
    InaccessibleClassProblem problem =
        new InaccessibleClassProblem(file, null, symbol, AccessModifier.DEFAULT);

    Assert.assertEquals(
        "Class Foo has default access and is referenced by foo (different package)",
        problem.toString());
  }
}
