/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.opensource.lts;

import com.google.common.base.Preconditions;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Map;

final class RepositoryTestCase {

  private final URL gitUrl;
  private final String name;
  private final String gitTag;
  private final Modification modification;
  private final String commands;

  URL getGitUrl() {
    return gitUrl;
  }

  String getName() {
    return name;
  }

  String getGitTag() {
    return gitTag;
  }

  String getCommands() {
    return commands;
  }

  Modification getModification() {
    return modification;
  }

  RepositoryTestCase(
      String name, URL gitUrl, String gitTag, Modification modification, String commands) {
    this.gitUrl = Preconditions.checkNotNull(gitUrl);
    this.name = Preconditions.checkNotNull(name);
    this.gitTag = Preconditions.checkNotNull(gitTag);
    this.modification = Preconditions.checkNotNull(modification);
    this.commands = Preconditions.checkNotNull(commands);
  }

  static RepositoryTestCase fromMap(Map<String, Object> input) throws MalformedURLException {
    String name = Preconditions.checkNotNull((String) input.get("name"));
    URL url = new URL(Preconditions.checkNotNull((String) input.get("url")));
    String gitTag = Preconditions.checkNotNull((String) input.get("tag"));
    Modification modification = modificationFromString((String) input.get("modification"));
    String commands = Preconditions.checkNotNull((String) input.get("commands"));

    return new RepositoryTestCase(name, url, gitTag, modification, commands);
  }

  static Modification modificationFromString(String input) {
    if ("Maven".equals(input)) {
      return Modification.MAVEN;
    } else if ("Gradle".equals(input)) {
      return Modification.GRADLE;
    }
    throw new IllegalArgumentException("Invalid input for modification: " + input);
  }

  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    if (!super.equals(object)) {
      return false;
    }

    RepositoryTestCase that = (RepositoryTestCase) object;
    return java.util.Objects.equals(gitUrl, that.gitUrl) && java.util.Objects
        .equals(name, that.name)
        && java.util.Objects.equals(gitTag, that.gitTag) && java.util.Objects
        .equals(modification, that.modification) && java.util.Objects
        .equals(commands, that.commands);
  }

  public int hashCode() {
    return Objects.hash(gitUrl, name, gitTag, modification, commands);
  }
}
