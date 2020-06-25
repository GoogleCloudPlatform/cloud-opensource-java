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

package org.apache.maven.dependency.graph;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;

import java.util.Locale;

/**
 * Platform-dependent project properties normalized from ${os.name} and ${os.arch}.
 * Netty uses these to select system-specific dependencies through the
 * <a href='https://github.com/trustin/os-maven-plugin'>os-maven-plugin</a>.
 */
public class OsProperties
{
  
  private static final CharMatcher LOWER_ALPHA_NUMERIC =
      CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('0', '9'));

  public static ImmutableMap<String, String> detectOsProperties() {
    return ImmutableMap.of(
        "os.detected.name",
        osDetectedName(),
        "os.detected.arch",
        osDetectedArch(),
        "os.detected.classifier",
        osDetectedName() + "-" + osDetectedArch());
  }

  private static String osDetectedName() {
    String osNameNormalized =
        LOWER_ALPHA_NUMERIC.retainFrom(System.getProperty("os.name").toLowerCase(Locale.ENGLISH));

    if (osNameNormalized.startsWith("macosx") || osNameNormalized.startsWith("osx")) {
      return "osx";
    } else if (osNameNormalized.startsWith("windows")) {
      return "windows";
    }
    // Since we only load the dependency graph, not actually use the
    // dependency, it doesn't matter a great deal which one we pick.
    return "linux";
  }

  private static String osDetectedArch() {
    String osArchNormalized =
        LOWER_ALPHA_NUMERIC.retainFrom(System.getProperty("os.arch").toLowerCase(Locale.ENGLISH));
    switch (osArchNormalized) {
      case "x8664":
      case "amd64":
      case "ia32e":
      case "em64t":
      case "x64":
        return "x86_64";
      default:
        return "x86_32";
    }
  }

}
