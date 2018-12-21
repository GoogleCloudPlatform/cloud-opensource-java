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

import com.google.auto.value.AutoValue;
import com.google.cloud.tools.opensource.dependencies.DependencyPath;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/**
 * The result of checking linkages in one jar file.
 */
@AutoValue
public abstract class JarLinkageReport {
  /**
   * Returns the absolute path of the jar file containing source classes of linkage errors
   */
  abstract Path getJarPath();
  
  /**
   * Returns the dependency path(s) to this artifact.
   */
  abstract ImmutableList<DependencyPath> getDependencyPaths();

  abstract ImmutableList<StaticLinkageError<ClassSymbolReference>> getMissingClassErrors();

  abstract ImmutableList<StaticLinkageError<MethodSymbolReference>> getMissingMethodErrors();

  abstract ImmutableList<StaticLinkageError<FieldSymbolReference>> getMissingFieldErrors();

  static Builder builder() {
    return new AutoValue_JarLinkageReport.Builder().setDependencyPaths(ImmutableList.of());
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setJarPath(Path value);
    abstract Builder setDependencyPaths(Iterable<DependencyPath> paths);

    abstract Builder setMissingClassErrors(
        Iterable<StaticLinkageError<ClassSymbolReference>> errors);

    abstract Builder setMissingMethodErrors(
        Iterable<StaticLinkageError<MethodSymbolReference>> errors);

    abstract Builder setMissingFieldErrors(
        Iterable<StaticLinkageError<FieldSymbolReference>> errors);

    abstract JarLinkageReport build();
  }
  
  @Override
  public String toString() {
    String indent = "  ";
    StringBuilder builder = new StringBuilder();
    int totalErrors = getTotalErrorCount();

    builder.append(getJarPath().getFileName() + " (" + totalErrors + " errors):\n");
    for (DependencyPath path : getDependencyPaths()) {
      builder.append(indent + "Linked from: " + path);
      builder.append("\n");
    }
    for (StaticLinkageError<ClassSymbolReference> missingClass : getMissingClassErrors()) {
      builder.append(indent + missingClass);
      builder.append("\n");
    }
    for (StaticLinkageError<MethodSymbolReference> missingMethod : getMissingMethodErrors()) {
      builder.append(indent + missingMethod);
      builder.append("\n");
    }
    for (StaticLinkageError<FieldSymbolReference> missingField : getMissingFieldErrors()) {
      builder.append(indent + missingField);
      builder.append("\n");
    }
    return builder.toString();
  }

  int getTotalErrorCount() {
    return getMissingClassErrors().size() + getMissingMethodErrors().size()
        + getMissingFieldErrors().size();
  }
}
