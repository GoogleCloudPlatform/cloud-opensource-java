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

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class LinkageCheckerPluginFunctionalTest extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """
apply plugin: 'com.google.cloud.tools.linkagechecker'
apply plugin: 'java'
        """
  }

  def "can successfully invalidate incompatible dependencies in a project"() {
    buildFile << """
        repositories {
          mavenCentral()
        }
        
        // These two have incompatible dependencies
        // https://github.com/grpc/grpc-java/issues/7002
        dependencies {
          compile 'com.google.cloud:google-cloud-logging:1.101.1'
          compile 'io.grpc:grpc-core:1.29.0'
        }
        
        linkageChecker {
          configurations = ['compile']
          reportOnlyReachable = true
        }
        """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('linkageCheck')
        .withPluginClasspath()
        .build()

    then:
    result.output.contains("Class io.grpc.internal.BaseDnsNameResolverProvider is not found")
    result.task(":linkageCheck").outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS
  }
}
