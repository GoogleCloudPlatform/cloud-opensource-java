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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * The result of checking linkages in one jar file.
 */
@AutoValue
public abstract class JarLinkageReport {
  /**
   * Returns the absolute path of the jar file containing source classes of linkage errors
   */
  public abstract Path getJarPath();

  public abstract ImmutableList<SymbolNotResolvable<ClassSymbolReference>> getMissingClassErrors();

  public abstract ImmutableList<SymbolNotResolvable<MethodSymbolReference>> getMissingMethodErrors();

  public abstract ImmutableList<SymbolNotResolvable<FieldSymbolReference>> getMissingFieldErrors();

  static Builder builder() {
    return new AutoValue_JarLinkageReport.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setJarPath(Path value);

    abstract Builder setMissingClassErrors(
        Iterable<SymbolNotResolvable<ClassSymbolReference>> errors);

    abstract Builder setMissingMethodErrors(
        Iterable<SymbolNotResolvable<MethodSymbolReference>> errors);

    abstract Builder setMissingFieldErrors(
        Iterable<SymbolNotResolvable<FieldSymbolReference>> errors);

    abstract JarLinkageReport build();
  }

  @Override
  public String toString() {
    String indent = "  ";
    StringBuilder builder = new StringBuilder();
    int totalErrors = getErrorCount();

    builder.append(getJarPath().getFileName() + " (" + totalErrors + " errors):\n");
    for (SymbolNotResolvable<ClassSymbolReference> missingClass : getMissingClassErrors()) {
      builder.append(indent + missingClass);
      builder.append("\n");
    }
    for (SymbolNotResolvable<MethodSymbolReference> missingMethod : getMissingMethodErrors()) {
      builder.append(indent + missingMethod);
      builder.append("\n");
    }
    for (SymbolNotResolvable<FieldSymbolReference> missingField : getMissingFieldErrors()) {
      builder.append(indent + missingField);
      builder.append("\n");
    }
    return builder.toString();
  }

  /** Returns map from the cause of linkage errors to class names affected by the errors. */
  public ImmutableMultimap<LinkageErrorCause, String> getCauseToSourceClasses() {
    ImmutableListMultimap<LinkageErrorCause, SymbolNotResolvable<ClassSymbolReference>>
        groupedClassErrors = Multimaps.index(getMissingClassErrors(), LinkageErrorCause::from);

    ImmutableListMultimap<LinkageErrorCause, SymbolNotResolvable<MethodSymbolReference>>
        groupedMethodErrors = Multimaps.index(getMissingMethodErrors(), LinkageErrorCause::from);

    ImmutableListMultimap<LinkageErrorCause, SymbolNotResolvable<FieldSymbolReference>>
        groupedFieldErrors = Multimaps.index(getMissingFieldErrors(), LinkageErrorCause::from);

    // ImmutableSet ensures deterministic iteration order
    Set<LinkageErrorCause> combinedKeys =
        ImmutableSet.<LinkageErrorCause>builder()
            .addAll(groupedClassErrors.keySet())
            .addAll(groupedMethodErrors.keySet())
            .addAll(groupedFieldErrors.keySet())
            .build();

    ImmutableMultimap.Builder<LinkageErrorCause, String> builder = ImmutableMultimap.builder();
    for (LinkageErrorCause key : combinedKeys) {
      List<SymbolNotResolvable<? extends SymbolReference>> allErrorsForKey =
          Lists.newArrayList(
              Iterables.concat(
                  groupedClassErrors.get(key),
                  groupedMethodErrors.get(key),
                  groupedFieldErrors.get(key)));
      builder.putAll(
          key,
          allErrorsForKey.stream()
              .map(SymbolNotResolvable::getReference)
              .map(SymbolReference::getSourceClassName)
              .map(className -> className.split("\\$")[0]) // Removing inner classes
              .collect(toImmutableSet()));
    }
    return builder.build();
  }

  public int getErrorCount() {
    return getCauseToSourceClasses().size();
  }

  public JarLinkageReport reachableErrors() {
    Builder builder = builder();
    builder.setMissingClassErrors(
        getMissingClassErrors().stream()
            .filter(SymbolNotResolvable::isReachable)
            .collect(toImmutableList()));
    builder.setMissingMethodErrors(
        getMissingMethodErrors().stream()
            .filter(SymbolNotResolvable::isReachable)
            .collect(toImmutableList()));
    builder.setMissingFieldErrors(
        getMissingFieldErrors().stream()
            .filter(SymbolNotResolvable::isReachable)
            .collect(toImmutableList()));
    builder.setJarPath(getJarPath());
    return builder.build();
  }
}
