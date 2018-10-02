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

package com.google.cloud.tools.opensource.dashboard;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.aether.artifact.Artifact;

import com.google.cloud.tools.opensource.dependencies.Artifacts;

/**
 * Collection of test results for a single artifact.
 */
public final class ArtifactResults {

  private final Map<String, Boolean> results = new HashMap<>();
  private final Artifact artifact;
  private String exceptionMessage;

  public ArtifactResults(Artifact artifact) {
    this.artifact = artifact;
  }

  public void setExceptionMessage(String exceptionMessage) {
    this.exceptionMessage = exceptionMessage;
  }

  void addResult(String testName, boolean result) {
    results.put(testName, result);
  }

  /**
   * @return true for pass, false for fail, null for unknown test
   */
  @Nullable
  public Boolean getResult(String testName) {
    return results.get(testName);
  }

  public String getCoordinates() {
    return Artifacts.toCoordinates(artifact);
  }

  /**
   *  @return message of exception occurred when running test, null for no exception
   */
  public String getExceptionMessage() {
    return exceptionMessage;
  }
}
