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

import com.google.cloud.tools.opensource.classpath.LinkageChecker;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Task to run Linkage Checker for the dependencies of the Gradle project.
 */
public class LinkageCheckTask extends DefaultTask {
  @TaskAction
  public void run() throws IOException {
    LinkageCheckerPluginExtension extension =
        getProject().getExtensions().findByType(LinkageCheckerPluginExtension.class);
    if (extension == null) {
      extension = new LinkageCheckerPluginExtension();
    }

    // TODO(suztomo): run linkage checker for the dependencies of the Gradle project.
    String message = extension.getMessage();
    LinkageChecker linkageChecker = LinkageChecker.create(ImmutableList.of());
    System.out.println("Hello from " + linkageChecker + ": " + message);
  }
}
