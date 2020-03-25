/*
 * Copyright 2019 Google LLC.
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

import static org.junit.Assert.assertEquals;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import java.nio.file.Paths;
import org.junit.Test;

public class ClassInJarTest {
  @Test
  public void testCreation() {
    ClassFile classInJar =
        new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "com.test.Foo");
    assertEquals("com.test.Foo", classInJar.getBinaryName());
    assertEquals(Paths.get("foo", "bar.jar"), classInJar.getClassPathEntry());
  }

  @Test
  public void testNull() {
    new NullPointerTester().testConstructors(ClassFile.class, Visibility.PACKAGE);
  }

  @Test
  public void testEquality() {
    new EqualsTester()
        .addEqualityGroup(
            new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "com.test.Foo"),
            new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "com.test.Foo"))
        .addEqualityGroup(
            new ClassFile(new ClassPathEntry(Paths.get("abc", "bar.jar")), "com.test.Foo"))
        .addEqualityGroup(new ClassFile(new ClassPathEntry(Paths.get("foo", "bar.jar")), "abc.Boo"))
        .testEquals();
  }
}
