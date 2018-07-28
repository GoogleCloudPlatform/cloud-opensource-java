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

package com.google.cloud.tools.opensource.dependencies;

import java.util.Comparator;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Compare semantically by version.
 */
public class VersionComparator implements Comparator<String> {

  @Override
  public int compare(String version1, String version2) {
    return new ComparableVersion(version1).compareTo(new ComparableVersion(version2));
  }

}
