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

import static com.google.cloud.tools.opensource.classpath.TestHelper.absolutePathOfResource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassPath;
import org.junit.Before;
import org.junit.Test;

public class FixedSizeClassPathRepositoryTest {
  private FixedSizeClassPathRepository repository;

  @Before
  public void setup() throws URISyntaxException {
    Path path = absolutePathOfResource("testdata/api-common-1.7.0.jar");
    ClassPath classPath = new LinkageCheckClassPath(Arrays.asList(path));
    repository = new FixedSizeClassPathRepository(classPath, 3);
  }

  @Test
  public void testClear() throws ClassNotFoundException {
    repository.loadClass("com.google.api.core.ApiFuture");
    repository.loadClass("com.google.api.core.ApiClock");
    repository.clear();

    assertNull(
        "Cache should clear all entries", repository.findClass("com.google.api.core.ApiClock"));
  }

  @Test
  public void testCacheEvictionLeastRecentlyUsed() throws ClassNotFoundException {
    // Rename for clarity
    FixedSizeClassPathRepository repositoryWithSize3 = repository;
    repositoryWithSize3.loadClass("com.google.api.core.ApiFuture");
    repositoryWithSize3.loadClass("com.google.api.core.ApiClock");
    repositoryWithSize3.loadClass("com.google.api.core.NanoClock");
    repositoryWithSize3.loadClass("com.google.api.core.ApiFuture"); // again
    repositoryWithSize3.loadClass("com.google.api.core.ApiAsyncFunction"); // No room for 4th item

    assertNull(
        "Cache should evict the least-recently-used entry when its full",
        repositoryWithSize3.findClass("com.google.api.core.ApiClock"));
    assertNotNull(
        "Cache should retain recently-used entry",
        repositoryWithSize3.findClass("com.google.api.core.ApiFuture"));
  }

  @Test
  public void testBootPrefixedClassFile() throws URISyntaxException, ClassNotFoundException {
    // This JAR file contains com.google.firestore.v1beta1.FirestoreGrpc under BOOT-INF/classes.
    Path path = absolutePathOfResource("testdata/dummy-boot-inf-prefix.jar");

    FixedSizeClassPathRepository repository =
        new FixedSizeClassPathRepository(new LinkageCheckClassPath(Arrays.asList(path)));
    JavaClass javaClass =
        repository.loadClass("BOOT-INF.classes.com.google.firestore.v1beta1.FirestoreGrpc");
    assertEquals("com.google.firestore.v1beta1.FirestoreGrpc", javaClass.getClassName());
    assertEquals(
        "BOOT-INF.classes.com.google.firestore.v1beta1.FirestoreGrpc", javaClass.getFileName());

    repository.clear();
    assertNotNull(
        "Even after the cache is cleared, the repository should be able to load the class by name",
        repository.loadClass(javaClass.getClassName()));
  }
}
