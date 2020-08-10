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

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin to create a "linkageCheck" in a Gradle project.
 */
public class LinkageCheckerPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getExtensions().create("linkageChecker", LinkageCheckerPluginExtension.class);
    project.getTasks().create("linkageCheck", LinkageCheckTask.class);
  }
}
