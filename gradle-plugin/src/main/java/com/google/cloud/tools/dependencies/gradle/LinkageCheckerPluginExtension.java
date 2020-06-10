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

package com.google.cloud.tools.dependencies.gradle;

import com.google.common.collect.ImmutableSet;
import java.util.List;

/**
 * Properties to control the behavior of the Linkage Checker plugin.
 *
 * TODO(suztomo): Implement real configuration as in go/jdd-gradle-plugin.
 */
public class LinkageCheckerPluginExtension {

  private boolean reportOnlyReachable = false;

  public boolean isReportOnlyReachable() {
    return reportOnlyReachable;
  }

  public void setReportOnlyReachable(boolean reportOnlyReachable) {
    this.reportOnlyReachable = reportOnlyReachable;
  }

  private ImmutableSet<String> configurations = ImmutableSet.of();

  public ImmutableSet<String> getConfigurations() {
    return configurations;
  }

  public void setConfigurations(List<String> configurationNames) {
    configurations = ImmutableSet.copyOf(configurationNames);
  }
}
