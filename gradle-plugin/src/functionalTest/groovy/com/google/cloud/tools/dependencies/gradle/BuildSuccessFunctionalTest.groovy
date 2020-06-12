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

package com.google.cloud.tools.dependencies.gradle

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification


class BuildSuccessFunctionalTest extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """
        plugins {
            id 'java'
            id 'com.google.cloud.tools.linkagechecker'
        }
        """
  }

  def "can validate a project with no dependency conflicts"() {
    buildFile << """
        repositories {
          mavenCentral()
        }
        
        // Guava does not have any linkage error
        dependencies {
          compile 'com.google.guava:guava:28.2-jre'
        }
        
        linkageChecker {
          configurations = ['compile']
        }
        """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('linkageCheck')
        .withPluginClasspath()
        .build()

    then:
    def output = result.output
    output.contains("Task :linkageCheck")
    output.contains("BUILD SUCCESSFUL")
    result.task(":linkageCheck").outcome == SUCCESS
  }
}
