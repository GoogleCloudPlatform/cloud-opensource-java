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
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification


class ExclusionFileFunctionalTest extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  String exclusionFileName = 'linkage-checker-exclusion-file.xml'
  File exclusionFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """
        plugins {
            id 'java'
            id 'com.google.cloud.tools.linkagechecker'
        }
        """
  }

  def "can suppress linkage errors listed in exclusion files"() {
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
          exclusionFile = 'linkage-checker-exclusion-file.xml'
        }
        """

    exclusionFile = testProjectDir.newFile(exclusionFileName)
    exclusionFile << """
        <LinkageCheckerFilter>
          <LinkageError>
            <Source>
              <Class name="io.grpc.grpclb.SecretGrpclbNameResolverProvider" />
            </Source>
            <Reason>we do not use SecretGrpclbNameResolverProvider</Reason>
          </LinkageError>
        </LinkageCheckerFilter>
        """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('linkageCheck')
        .withPluginClasspath()
        .buildAndFail()

    then:
    result.output.contains("Task :linkageCheck")
    result.output.contains("BUILD FAILED")

    // BaseDnsNameResolverProvider is referenced by SecretGrpclbNameResolverProvider
    !result.output.contains("io.grpc.internal.BaseDnsNameResolverProvider")
    result.task(":linkageCheck").outcome == TaskOutcome.FAILED
  }
}
