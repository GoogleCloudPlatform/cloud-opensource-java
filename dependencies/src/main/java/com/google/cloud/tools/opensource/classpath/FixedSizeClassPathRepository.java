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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.ClassPathRepository;

/**
 * This class limits the size of its {@link JavaClass} cache to at most {@code maximumSize} entries.
 * When the cache reaches the limit, it evicts entries that have not been used recently.
 *
 * <p>This class avoids {@code OutOfMemoryError}s that occurs when parsing too many JAR files to
 * handle with {@link ClassPathRepository} or {@link
 * org.apache.bcel.util.MemorySensitiveClassPathRepository}, while providing reasonable speed by
 * caching frequently-used instances.
 *
 * <p>The default maximum size is 1000 entries. From the experiment with spring-cloud-gcp project,
 * this maximum size gives the best performance when running {@link
 * LinkageChecker#findSymbolProblems()}.
 *
 * @see <a href="https://github.com/google/guava/wiki/CachesExplained#size-based-eviction">Guava
 *     CachesExplained: Size-based Eviction</a>
 * @see <a href="https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/653"
 *     >Performance evaluation of FixedSizeClassPathRepository with different maximum cache size</a>
 */
final class FixedSizeClassPathRepository extends ClassPathRepository {

  private final Cache<String, JavaClass> loadedClass;

  /**
   * Mapping from class names to special class file locations.
   *
   * <p>While class name and class file name are the same in most cases, sometimes classes are not
   * placed in the root of a JAR file to support a framework-specific JAR structure. For example,
   * Spring Boot Gradle Java plugin places class files under "BOOT-INF/classes". To load such
   * classes by class name, this mapping keeps track of the special location once they are loaded.
   *
   * <ul>
   *   <li>Key: class name (value from {@link JavaClass#getClassName()}) which has a special class
   *       file name different from its class name. Example: {@code com.google.Foo}
   *   <li>Value: the special class file name as in {@link JavaClass#getFileName()}, a path that
   *       locates a class file in a class path. Example: {@code BOOT-INF.classes.com.google.Foo}
   * </ul>
   *
   * @see <a
   *     href="https://docs.spring.io/spring-boot/docs/current/reference/html/executable-jar.html"
   *     >Spring Boot Reference Guide: The Executable Jar Format</a>
   */
  private final Map<String, String> classFileNames;

  FixedSizeClassPathRepository(ClassPath path) {
    this(path, 1000);
  }

  @VisibleForTesting
  FixedSizeClassPathRepository(ClassPath path, long maximumSize) {
    super(path);
    loadedClass = CacheBuilder.newBuilder().maximumSize(maximumSize).build();
    this.classFileNames = new HashMap<>();
  }

  @Override
  public void storeClass(JavaClass javaClass) {
    String className = javaClass.getClassName();
    loadedClass.put(className, javaClass);
    javaClass.setRepository(this);

    String classFileName = javaClass.getFileName();
    if (!className.equals(classFileName)) {
      // When class file has special location not matching class name, remember it to load the class
      // file by class name.
      classFileNames.put(className, classFileName);
    }
  }

  @Override
  @Nullable
  public JavaClass findClass(String className) {
    return loadedClass.getIfPresent(className);
  }

  @Override
  public JavaClass loadClass(String className) throws ClassNotFoundException {
    // Check special location for the class. If it's not found, lookup by className instead.
    // Usually classFileName == className. But sometimes classFileName has a framework-specific
    // prefix. Example: "BOOT-INF.classes.com.google.Foo"
    String classFileName = classFileNames.getOrDefault(className, className);
    return super.loadClass(classFileName);
  }

  @Override
  public void clear() {
    loadedClass.invalidateAll();
  }
}
