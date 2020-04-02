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

import com.google.cloud.tools.opensource.dependencies.Bom;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;

/** Request to run Linkage Check. */
public class LinkageCheckRequest {

  private final ImmutableList<ClassPathEntry> classPath;
  private final ExcludedErrors excludedErrors;
  private final ImmutableSet<ClassPathEntry> entryPoints;
  private boolean reportOnlyReachable;

  ImmutableList<ClassPathEntry> getClassPath() {
    return classPath;
  }

  ExcludedErrors getExcludedErrors() {
    return excludedErrors;
  }

  /**
   * Returns JAR files to specify entry point classes. If the request does not filter errors by
   * class reachability, returns empty.
   */
  ImmutableSet<ClassPathEntry> getEntryPoints() {
    return entryPoints;
  }

  boolean reportOnlyReachable() {
    return reportOnlyReachable;
  }

  private LinkageCheckRequest(
      ImmutableList<ClassPathEntry> classPath,
      ExcludedErrors excludedErrors, boolean reportOnlyReachable,
      ImmutableSet<ClassPathEntry> entryPoints) {
    this.classPath = classPath;
    this.excludedErrors = excludedErrors;
    this.entryPoints = entryPoints;
    this.reportOnlyReachable = reportOnlyReachable;
  }

  public static Builder builder(Bom bom) {
    return new Builder(bom);
  }

  public static Builder builder(Iterable<ClassPathEntry> classPath) {
    return new Builder(classPath);
  }

  /** Builder for {@link LinkageCheckRequest}. */
  public static class Builder {
    // Either classPath or bom is non-null
    private ImmutableList<ClassPathEntry> classPath;
    private Bom bom;

    private Path exclusionFile;

    private boolean reportOnlyReachable = false;
    private ImmutableSet.Builder<ClassPathEntry> entryPoints = ImmutableSet.builder();

    /**
     * Builder for {@link LinkageChecker} to find linkage errors in {@code bom}.
     *
     * @param bom BOM to create class path to find linkage errors in
     */
    private Builder(Bom bom) {
      this.bom = Preconditions.checkNotNull(bom);
    }

    /**
     * Builder for {@link LinkageChecker} to find linkage errors in {@code classPath}.
     *
     * @param classPath class path to find linkage errors in
     */
    private Builder(Iterable<ClassPathEntry> classPath) {
      this.classPath = ImmutableList.copyOf(classPath);
    }

    /** Sets JAR files to specify entry point classes in reachability. */
    public Builder reportOnlyReachable(Iterable<ClassPathEntry> entryPoints) {
      reportOnlyReachable = true;
      this.entryPoints.addAll(entryPoints);
      return this;
    }

    /** Sets {@code exclusionFile} to suppress linkage errors. */
    public Builder exclusionFile(Path exclusionFile) {
      this.exclusionFile = exclusionFile;
      return this;
    }

    /**
     * Returns new {@link LinkageCheckRequest} based on the configuration passed to the {@link
     * Builder}.
     */
    public LinkageCheckRequest build() throws IOException {

      // If exclusionFile is null, then it gets default exclusion rule
      ExcludedErrors excludedErrors = ExcludedErrors.create(exclusionFile);
      if (bom != null) {
        ImmutableList<Artifact> managedDependencies = bom.getManagedDependencies();

        ClassPathBuilder classPathBuilder = new ClassPathBuilder();
        ClassPathResult classPathResult = classPathBuilder.resolve(managedDependencies);
        ImmutableList<ClassPathEntry> classpath = classPathResult.getClassPath();

        // When checking a BOM, entry point classes are the ones in the artifacts listed in the BOM
        List<ClassPathEntry> artifactsInBom = classpath.subList(0, managedDependencies.size());
        entryPoints.addAll(artifactsInBom);
        return new LinkageCheckRequest(classpath, excludedErrors, reportOnlyReachable, entryPoints.build()
        );
      } else {
        // classPath is not null
        return new LinkageCheckRequest(classPath, excludedErrors, reportOnlyReachable, entryPoints.build()
        );
      }
    }
  }
}
