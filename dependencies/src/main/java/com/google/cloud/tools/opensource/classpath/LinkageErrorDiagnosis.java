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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import org.eclipse.aether.artifact.Artifact;

/** Diagnosis on {@link LinkageErrorCause} using Maven artifacts. */
@AutoValue
abstract class LinkageErrorDiagnosis {
  // /** Returns the linkage error that is the subject of the diagnosis. */
  //  abstract SymbolNotResolvable getSymbolNotResolvable();
  /** Returns the cause of linkage error, which is the subject of the diagnosis. */
  abstract LinkageErrorCause getLinkageErrorCause();

  /**
   * Returns the artifact which the source classes of the {@link SymbolNotResolvable#getReference()}
   * belongs to.
   */
  abstract Artifact getSourceArtifact();

  abstract Artifact getArtifactWithResolvableSymbol();

  /** Returns the artifact used in the linkage check. */
  abstract Artifact getArtifactInClassPath();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setLinkageErrorCause(LinkageErrorCause linkageErrorCause);

    abstract Builder setSourceArtifact(Artifact artifact);

    abstract Builder setArtifactWithResolvableSymbol(Artifact artifact);

    abstract Builder setArtifactInClassPath(Artifact artifact);

    abstract LinkageErrorDiagnosis build();
  }

  static Builder builder() {
    return new AutoValue_LinkageErrorDiagnosis.Builder();
  }

  @Override
  public final String toString() {
    StringBuilder builder = new StringBuilder();

    //  SymbolNotResolvable symbolNotResolvable = getSymbolNotResolvable();
    //  SymbolReference reference = symbolNotResolvable.getReference();
    LinkageErrorCause cause = getLinkageErrorCause();
    builder.append(cause.toString());
    builder.append(". The source classes belong to ");
    builder.append(getSourceArtifact());
    builder.append(". The symbol was resolvable in ");

    Artifact artifactWithResolvableSymbol = getArtifactWithResolvableSymbol();
    builder.append(artifactWithResolvableSymbol);
    builder.append(", but the class path has ");
    Artifact artifactInClassPath = getArtifactInClassPath();
    builder.append(artifactInClassPath);

    return builder.toString();
  }
}
