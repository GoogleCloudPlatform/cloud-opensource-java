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

import java.nio.file.Path
import java.nio.file.Paths


class ExclusionFileFunctionalTest extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  String exclusionFileName = 'linkage-checker-exclusion-file.xml'

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """
        plugins {
            id 'java'
            id 'com.google.cloud.tools.linkagechecker'
        }
        """
  }

  def "can suppress linkage errors listed in exclusion files (relative path)"() {
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
          exclusionFile = '$exclusionFileName'
        }
        """

    File exclusionFile = testProjectDir.newFile(exclusionFileName)
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
        .withArguments('linkageCheck', '--stacktrace')
        .withPluginClasspath()
        .buildAndFail()

    then:
    result.output.contains("Task :linkageCheck")
    result.output.contains("BUILD FAILED")
    // Ensure it outputs the linkage errors
    result.output.contains("Class com.jcraft.jzlib.JZlib is not found")

    // BaseDnsNameResolverProvider is referenced by SecretGrpclbNameResolverProvider, which we
    // suppress via the exclusion file.
    !result.output.contains("io.grpc.internal.BaseDnsNameResolverProvider")
    // There are other linkage errors.
    result.task(":linkageCheck").outcome == TaskOutcome.FAILED
  }

  def "can suppress linkage errors listed in exclusion files (absolute path)"() {
    Path exclusionFileNameAbsolutePath = Paths.get(exclusionFileName).toAbsolutePath()

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
          exclusionFile = '$exclusionFileNameAbsolutePath'
        }
        """

    File exclusionFile = exclusionFileNameAbsolutePath.toFile()
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
        .withArguments('linkageCheck', '--stacktrace')
        .withPluginClasspath()
        .buildAndFail()

    then:
    result.output.contains("Task :linkageCheck")
    result.output.contains("BUILD FAILED")
    // Ensure it outputs the linkage errors
    result.output.contains("Class com.jcraft.jzlib.JZlib is not found")

    // BaseDnsNameResolverProvider is referenced by SecretGrpclbNameResolverProvider, which we
    // suppress via the exclusion file.
    !result.output.contains("io.grpc.internal.BaseDnsNameResolverProvider")
    // There are other linkage errors.
    result.task(":linkageCheck").outcome == TaskOutcome.FAILED
  }

  def "can pass the build by suppressing all linkage errors"() {
    buildFile << """
        repositories {
          mavenCentral()
        }
        
        // These two have incompatible dependencies
        // https://github.com/grpc/grpc-java/issues/7002
        dependencies {
          compile 'com.google.api:gax:1.57.0'
        }
        
        linkageChecker {
          configurations = ['compile']
          exclusionFile = '$exclusionFileName'
        }
        """

    // When resolved by Gradle's dependency resolution logic, gax has the following linkage errors.
    File exclusionFile = testProjectDir.newFile(exclusionFileName)
    exclusionFile << """
        <LinkageCheckerFilter>
          <LinkageError>
            <Target>
              <Class name="org.apache.avalon.framework.logger.Logger" />
            </Target>
            <Source>
              <Class name="org.apache.commons.logging.impl.AvalonLogger" />
            </Source>
            <Reason>we do not use the logger</Reason>
          </LinkageError>
          <LinkageError>
            <Target>
              <Class name="org.apache.log.Hierarchy" />
            </Target>
            <Source>
              <Class name="org.apache.commons.logging.impl.LogKitLogger" />
            </Source>
            <Reason>we do not use the logger</Reason>
          </LinkageError>
          <LinkageError>
            <Target>
              <Class name="org.apache.log.Logger" />
            </Target>
            <Source>
              <Class name="org.apache.commons.logging.impl.LogKitLogger" />
            </Source>
            <Reason>we do not use the logger</Reason>
          </LinkageError>
        </LinkageCheckerFilter>
        """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('linkageCheck', '--stacktrace')
        .withPluginClasspath()
        .build()

    then:
    result.output.contains("Task :linkageCheck")
    result.output.contains("BUILD SUCCESS")
    !result.output.contains("org.apache.commons.logging")
    result.task(":linkageCheck").outcome == TaskOutcome.SUCCESS
  }
}
