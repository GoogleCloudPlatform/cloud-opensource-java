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
import com.google.cloud.tools.opensource.dependencies.Artifacts;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;

/**
 * The result of a diagnosis on {@link LinkageErrorCause}. The diagnosis tells which Maven artifact
 * has a resolvable symbol for {@link LinkageErrorCause#getSymbol()} and which Maven artifact is
 * picked up instead by the dependency mediation logic.
 *
 * @see <a
 *     href='https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Transitive_Dependencies'>
 *     Introduction to the Dependency Mechanism: Dependency mediation</a>
 */
@AutoValue
public abstract class LinkageErrorDiagnosis {
  /** Returns the cause of linkage error, which is the subject of the diagnosis. */
  abstract LinkageErrorCause getLinkageErrorCause();

  /**
   * Returns the artifact which the source classes of the {@link SymbolNotResolvable#getReference()}
   * belongs to.
   */
  abstract Artifact getSourceArtifact();

  /**
   * Returns the artifact that has the resolvable symbol for {@link
   * SymbolNotResolvable#getReference()}. Null if such artifact is not found in the dependencies of
   * {@link #getSourceArtifact()}.
   */
  @Nullable
  abstract Artifact getArtifactWithResolvableSymbol();

  /**
   * Returns the artifact picked up by the dependency mediation algorithm instead of {@link
   * #getArtifactWithResolvableSymbol()}. Null if {@link #getArtifactWithResolvableSymbol()} is
   * null.
   */
  @Nullable
  abstract Artifact getArtifactInClassPath();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setLinkageErrorCause(LinkageErrorCause linkageErrorCause);

    abstract Builder setSourceArtifact(Artifact artifact);

    abstract Builder setArtifactWithResolvableSymbol(Artifact artifact);

    abstract Builder setArtifactInClassPath(Artifact artifact);

    abstract LinkageErrorDiagnosis build();
  }

  private static Builder builder() {
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

    Artifact artifactWithResolvableSymbol = getArtifactWithResolvableSymbol();
    if (artifactWithResolvableSymbol == null) {
      builder.append(". The dependencies of the artifact do not have the symbol.");
    } else {
      builder.append(". The symbol was resolvable in ");
      builder.append(artifactWithResolvableSymbol);
      builder.append(", but the class path has ");
      Artifact artifactInClassPath = getArtifactInClassPath();
      builder.append(artifactInClassPath);
    }

    return builder.toString();
  }

  /** Returns a map from the cause of linkage errors to the diagnosis. */
  public static ImmutableMap<LinkageErrorCause, LinkageErrorDiagnosis> diagnoseJarLinkageReport(
      JarLinkageReport jarLinkageReport, Map<Path, Artifact> pathToArtifact)
      throws RepositoryException, IOException {
    ImmutableMap.Builder<LinkageErrorCause, LinkageErrorDiagnosis> causeToDiagnosis =
        ImmutableMap.builder();
    Artifact sourceArtifact = pathToArtifact.get(jarLinkageReport.getJarPath());

    ImmutableMultimap<LinkageErrorCause, String> causeToSourceClasses =
        jarLinkageReport.getCauseToSourceClasses();
    if (causeToSourceClasses.isEmpty()) {
      return causeToDiagnosis.build();
    }

    ImmutableMap<String, Artifact> selectedArtifacts =
        Maps.uniqueIndex(pathToArtifact.values(), Artifacts::makeKey);

    ImmutableMap<Path, Artifact> pathToArtifactForJar =
        ClassPathBuilder.getPathToArtifact(ImmutableList.of(sourceArtifact));

    for (LinkageErrorCause cause : causeToSourceClasses.keySet()) {
      switch (cause.getReason()) {
        case CLASS_NOT_FOUND:
          LinkageErrorDiagnosis diagnosis =
              diagnoseMissingClass(cause, sourceArtifact, selectedArtifacts, pathToArtifactForJar);
          causeToDiagnosis.put(cause, diagnosis);
          break;
        default:
          // TODO: Implement other cases (SYMBOL_NOT_FOUND, INACCESSIBLE_CLASS, etc.)
      }
    }

    return causeToDiagnosis.build();
  }

  private static LinkageErrorDiagnosis diagnoseMissingClass(
      LinkageErrorCause cause,
      Artifact sourceArtifact,
      Map<String, Artifact> selectedArtifacts,
      Map<Path, Artifact> pathToArtifactForJar)
      throws IOException {

    for (Path path : pathToArtifactForJar.keySet()) {
      boolean hasMissingClass =
          ClassDumper.listClassesInJar(path).stream()
              .anyMatch(javaClass -> javaClass.getClassName().equals(cause.getSymbol()));
      if (hasMissingClass) {
        Artifact artifactWithResolvableSymbol = pathToArtifactForJar.get(path);
        String versionLessCoordinates = Artifacts.makeKey(artifactWithResolvableSymbol);
        Artifact selectedArtifact = selectedArtifacts.get(versionLessCoordinates);

        LinkageErrorDiagnosis diagnosis =
            LinkageErrorDiagnosis.builder()
                .setLinkageErrorCause(cause)
                .setSourceArtifact(sourceArtifact)
                .setArtifactInClassPath(selectedArtifact)
                .setArtifactWithResolvableSymbol(artifactWithResolvableSymbol)
                .build();
        return diagnosis;
      }
    }

    LinkageErrorDiagnosis diagnosis =
        LinkageErrorDiagnosis.builder()
            .setLinkageErrorCause(cause)
            .setSourceArtifact(sourceArtifact)
            .setArtifactWithResolvableSymbol(null)
            .setArtifactInClassPath(null)
            .build();
    return diagnosis;
  }
}
