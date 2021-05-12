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

package org.apache.beam.gradle

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.json.JsonOutput
import groovy.json.JsonSlurper


if (config.getName() != "errorprone" && !inDependencyUpdates) {
  config.resolutionStrategy {
    // Filtering versionless coordinates that depend on BOM. Beam project needs to set the
    // versions for only handful libraries when building the project (BEAM-9542).
    def librariesWithVersion = project.library.java.values().findAll { it.split(':').size() > 2 }
    force librariesWithVersion
  }
}

