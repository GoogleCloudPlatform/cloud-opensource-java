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

import javax.annotation.Nullable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.ClassPathRepository;

/**
 * This repository behaves same as {@link ClassPathRepository} except that it does not remember
 * class at all.
 */
final class NoCachingClassPathRepository extends ClassPathRepository {

  public NoCachingClassPathRepository(final ClassPath path) {
    super(path);
  }

  @Override
  public void storeClass(final JavaClass clazz) {
    clazz.setRepository(this);
  }

  /** Find an already defined (cached) JavaClass object by name. */
  @Override
  @Nullable
  public JavaClass findClass(final String className) {
    return null;
  }

  @Override
  public ClassPath getClassPath() {
    return super.getClassPath();
  }

  @Override
  public void clear() {}
}
