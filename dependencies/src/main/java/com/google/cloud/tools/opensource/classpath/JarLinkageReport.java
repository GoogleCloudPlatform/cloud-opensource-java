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
import java.util.stream.Collectors;

/**
 * The result of checking linkages in one jar file.
 */
@AutoValue
public abstract class JarLinkageReport {
  /**
   * Returns the absolute path of the jar file containing source classes of linkage errors
   */
  public abstract Path getJarPath();

  public abstract ImmutableList<StaticLinkageError<ClassSymbolReference>> getMissingClassErrors();

  public abstract ImmutableList<StaticLinkageError<MethodSymbolReference>> getMissingMethodErrors();

  public abstract ImmutableList<StaticLinkageError<FieldSymbolReference>> getMissingFieldErrors();

  static Builder builder() {
    return new AutoValue_JarLinkageReport.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setJarPath(Path value);

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

  /** Returns map from the cause of linkage errors and class names affected by the errors. */
  public ImmutableMultimap<LinkageErrorCause, String> getCauseToSourceClasses() {
    ImmutableListMultimap<LinkageErrorCause, StaticLinkageError<ClassSymbolReference>>
        groupedClassErrors = Multimaps.index(getMissingClassErrors(), LinkageErrorCause::from);

    ImmutableListMultimap<LinkageErrorCause, StaticLinkageError<MethodSymbolReference>>
        groupedMethodErrors = Multimaps.index(getMissingMethodErrors(), LinkageErrorCause::from);

    ImmutableListMultimap<LinkageErrorCause, StaticLinkageError<FieldSymbolReference>>
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
      List<StaticLinkageError<? extends SymbolReference>> allErrorsForKey =
          Lists.newArrayList(
              Iterables.concat(
                  groupedClassErrors.get(key),
                  groupedMethodErrors.get(key),
                  groupedFieldErrors.get(key)));
      builder.putAll(
          key,
          allErrorsForKey.stream()
              .map(StaticLinkageError::getReference)
              .map(SymbolReference::getSourceClassName)
              .collect(toImmutableSet()));
    }
    return builder.build();
  }

  public int getTotalErrorCount() {
    return getMissingClassErrors().size() + getMissingMethodErrors().size()
        + getMissingFieldErrors().size();
  }
}
