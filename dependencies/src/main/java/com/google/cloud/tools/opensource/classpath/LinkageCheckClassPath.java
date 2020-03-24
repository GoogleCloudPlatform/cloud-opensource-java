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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.bcel.util.ClassPath;

/**
 * Class path to load class files for {@link LinkageChecker}. When loading a resource for a
 * class, {@link LinkageCheckClassPath} ensures that the class file comes from only the extension
 * class loader or the class path specified at the constructor argument.
 *
 * <p>Background: BCEL's {@link ClassPath#getInputStream(String, String)} uses the system class
 * loader to read class files when {@link
 * org.apache.bcel.util.ClassPathRepository#loadClass(String)} is called, meaning that it loads
 * other classes outside the class path specified at its constructor argument. In other words,
 * classes used in this cloud-opensource-java project (including Guava 26) would be unexpectedly
 * loaded by the system class loader when running linkage check, if BCEL's {@link ClassPath}
 * is naively used.
 *
 * <p>This class is introduced to avoid the mix-up of the class paths. It loads resources only from
 * the extension class loader, which does not include the class path of this project, or the class
 * path specified at the constructor argument.
 *
 * @see <a
 *     href="https://commons.apache.org/proper/commons-bcel/apidocs/org/apache/bcel/util/ClassPath.html">BCEL's
 *     ClassPath</a>
 */
public class LinkageCheckClassPath extends ClassPath {
  private final ClassLoader extensionClassLoader;

  /**
   * Constructs a classpath for check.
   *
   * @param paths list of absolute paths for the elements in the class path
   */
  LinkageCheckClassPath(List<Path> paths) {
    super(paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
    extensionClassLoader = ClassLoader.getSystemClassLoader().getParent();
  }

  /**
   * Returns an input stream for a resource in the class path. This uses either the
   * extension class loader or the class path specified at the constructor argument, ensuring that
   * the resource is not loaded from the class path of this tool itself.
   * 
   * @param name a slash separated relative path such as "java/lang/String"
   * @param suffix the extension of the file in the classpath such as ".class" or ".xml"
   */
  @Override
  public InputStream getInputStream(String name, String suffix) throws IOException {
    InputStream inputStream = extensionClassLoader.getResourceAsStream(name + suffix);
    if (inputStream != null) {
      return inputStream;
    }
    return getClassFile(name, suffix).getInputStream();
  }
}
