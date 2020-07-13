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

package com.google.cloud.tools.opensource.classpath;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.iso_relax.verifier.VerifierConfigurationException;
import org.xml.sax.SAXException;

class ExcludedErrors {
  private final ImmutableList<LinkageErrorMatcher> exclusionMatchers;

  /**
   * Creates exclusion matchers from {@code exclusionFile} with default rules. If {@code
   * exclusionFile} is {@code null}, then returns default exclusion rules.
   */
  static ExcludedErrors create(@Nullable Path exclusionFile) throws IOException {
    ImmutableList.Builder<LinkageErrorMatcher> exclusionMatchers = ImmutableList.builder();

    try {
      URL defaultRuleUrl =
          LinkageChecker.class
              .getClassLoader()
              .getResource("linkage-checker-exclusion-default.xml");
      ImmutableList<LinkageErrorMatcher> defaultMatchers = ExclusionFiles.parse(defaultRuleUrl);
      exclusionMatchers.addAll(defaultMatchers);
    } catch (SAXException | VerifierConfigurationException ex) {
      throw new IOException("Could not read default exclusion rule", ex);
    }

    try {
      if (exclusionFile != null) {
        exclusionMatchers.addAll(ExclusionFiles.parse(exclusionFile));
      }
    } catch (SAXException | VerifierConfigurationException ex) {
      throw new IOException("Could not read exclusion rule file " + exclusionFile, ex);
    }

    return new ExcludedErrors(exclusionMatchers.build());
  }

  private ExcludedErrors(Iterable<LinkageErrorMatcher> exclusionMatchers) {
    this.exclusionMatchers = ImmutableList.copyOf(exclusionMatchers);
  }

  /**
   * Returns true if the exclusion rules contain references to {@code linkageProblem} from {@code
   * sourceClass}.
   */
  boolean contains(LinkageProblem linkageProblem) {
    for (LinkageErrorMatcher matcher : exclusionMatchers) {
      if (matcher.match(linkageProblem)) {
        return true;
      }
    }
    return false;
  }
}
