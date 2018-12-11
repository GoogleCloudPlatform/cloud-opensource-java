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
  public abstract Path getJarPath();

  public abstract ImmutableList<LinkageErrorMissingClass> getMissingClassErrors();
  public abstract ImmutableList<LinkageErrorMissingMethod> getMissingMethodErrors();
  public abstract ImmutableList<LinkageErrorMissingField> getMissingFieldErrors();

  static Builder builder() {
    return new AutoValue_JarLinkageReport.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setJarPath(Path value);
    abstract Builder setMissingClassErrors(Iterable<LinkageErrorMissingClass> value);
    abstract Builder setMissingMethodErrors(Iterable<LinkageErrorMissingMethod> value);
    abstract Builder setMissingFieldErrors(Iterable<LinkageErrorMissingField> value);
    abstract JarLinkageReport build();
  }
  
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    int totalErrors = getTotalErrorCount();
    builder.append(getJarPath().getFileName() + " (" + totalErrors + " errors):\n");
    String indent = "  ";
    for (LinkageErrorMissingClass missingClass : getMissingClassErrors()) {
      builder.append(indent + missingClass.getReference());
      builder.append("\n");
    }
    for (LinkageErrorMissingMethod missingMethod : getMissingMethodErrors()) {
      builder.append(indent + missingMethod.getReference());
      builder.append("\n");
    }
    for (LinkageErrorMissingField missingField : getMissingFieldErrors()) {
      builder.append(indent + missingField.getReference());
      builder.append("\n");
    }
    return builder.toString();
  }

  int getTotalErrorCount() {
    return getMissingClassErrors().size() + getMissingMethodErrors().size()
        + getMissingFieldErrors().size();
  }
}
