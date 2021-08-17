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


class BuildStatusFunctionalTest extends Specification {
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
    File settingsFile = testProjectDir.newFile("settings.gradle")
    settingsFile << """
        rootProject.name = 'test-123'
    """
  }

  def "can validate a project with no dependency conflicts"() {
    buildFile << """
        repositories {
          mavenCentral()
        }
        
        // Guava does not have any linkage errors
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
        .withArguments('linkageCheck', '--stacktrace')
        .withPluginClasspath()
        .build()

    then:
    result.output.contains("Task :linkageCheck")
    result.output.contains("BUILD SUCCESSFUL")
    result.task(":linkageCheck").outcome == TaskOutcome.SUCCESS
  }

  def "can invalidate incompatible dependencies in a project"() {
    buildFile << """
        repositories {
          mavenCentral()
        }
        group = 'g'
        version = '0.1.0-SNAPSHOT'

        // These two have incompatible dependencies
        // https://github.com/grpc/grpc-java/issues/7002
        dependencies {
          compile 'com.google.cloud:google-cloud-logging:1.101.1'
          compile 'io.grpc:grpc-core:1.29.0'
        }
        
        linkageChecker {
          configurations = ['compile']
        }
        """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('linkageCheck','--stacktrace')
        .withPluginClasspath()
        .buildAndFail()

    then:
    result.output.contains('''Class io.grpc.internal.BaseDnsNameResolverProvider is not found;
        |  referenced by 1 class file
        |    io.grpc.grpclb.SecretGrpclbNameResolverProvider (io.grpc:grpc-grpclb:1.28.1)
        |  Cause:
        |    Dependency conflict: io.grpc:grpc-core:1.29.0 does not define Class io.grpc.internal.BaseDnsNameResolverProvider but io.grpc:grpc-core:1.28.1 defines it.
        |      selected: io.grpc:grpc-core:1.29.0 (compile)
        |      unselected: com.google.cloud:google-cloud-logging:1.101.1 (compile) / com.google.api:gax-grpc:1.56.0 (compile) / io.grpc:grpc-alts:1.28.1 (compile) / io.grpc:grpc-grpclb:1.28.1 (compile) / io.grpc:grpc-core:1.28.1 (compile)'''.stripMargin())

    result.output.contains("Problematic artifacts in the dependency tree:")
    result.output.contains("""
        |io.grpc:grpc-grpclb:1.28.1 is at:
        |  g:test-123:0.1.0-SNAPSHOT / com.google.cloud:google-cloud-logging:1.101.1 / com.google.api:gax-grpc:1.56.0 / io.grpc:grpc-alts:1.28.1 / io.grpc:grpc-grpclb:1.28.1
        |""".stripMargin())

    // "(omitted for duplicate)" should come after the non-omitted items
    result.output.contains("""
        |io.grpc:grpc-alts:1.28.1 is at:
        |  g:test-123:0.1.0-SNAPSHOT / com.google.cloud:google-cloud-logging:1.101.1 / com.google.api:gax-grpc:1.56.0 / io.grpc:grpc-alts:1.28.1
        |""".stripMargin())

    // Ensure the node closest to the root is printed
    result.output.contains("g:test-123:0.1.0-SNAPSHOT / io.grpc:grpc-core:1.29.0")
    // Ensure that the relationship between grpc-netty-shaded to grpc-core is only printed once
    result.output.count("io.grpc:grpc-netty-shaded:1.28.1 / io.grpc:grpc-core:1.29.0") == 1
    result.task(":linkageCheck").outcome == TaskOutcome.FAILED
  }

  def "can handle artifacts with classifiers "() {
    buildFile << """
        repositories {
          mavenCentral()
        }
        
        dependencies {
          // The tests-classifier artifacts should not stop Linkage Checker
          compile 'com.google.cloud:google-cloud-core:1.95.4'
          compile 'com.google.cloud:google-cloud-core:1.95.4:tests'
        }
        
        linkageChecker {
          configurations = ['compile']
        }
        """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('linkageCheck', '--stacktrace')
        .withPluginClasspath()
        .buildAndFail()

    then:
    result.output.contains("Task :linkageCheck")
    result.output.contains("Linkage Checker rule found 72 errors:")
    result.output.contains("""Class org.junit.Assert is not found;
        |  referenced by 26 class files
        |    com.google.cloud.ServiceOptionsTest (com.google.cloud:google-cloud-core:jar:tests:1.95.4)
        |    com.google.cloud.BatchResultTest (com.google.cloud:google-cloud-core:jar:tests:1.95.4)
        |  """.stripMargin())
    result.task(":linkageCheck").outcome == TaskOutcome.FAILED
  }

  def "can handle artifacts with classifiers with transitive dependencies"() {
    buildFile << """
        repositories {
          mavenCentral()
        }
        
        dependencies {
          // google-cloud-bigquery depends on google-cloud-core (no classifier)
          compile 'com.google.cloud:google-cloud-bigquery:1.137.1'
          compile 'com.google.cloud:google-cloud-core:1.95.4:tests'
        }
        
        linkageChecker {
          configurations = ['compile']
        }
        """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('linkageCheck', '--stacktrace')
        .withPluginClasspath()
        .buildAndFail()

    then:
    result.output.contains("Task :linkageCheck")
    result.output.contains("Linkage Checker rule found 93 errors")
    result.output.contains("""Class org.junit.Assert is not found;
        |  referenced by 26 class files
        |    com.google.cloud.ServiceOptionsTest (com.google.cloud:google-cloud-core:jar:tests:1.95.4)
        |    com.google.cloud.BatchResultTest (com.google.cloud:google-cloud-core:jar:tests:1.95.4)
        |  """.stripMargin())
    !result.output.contains("StackOverflowError")
    result.task(":linkageCheck").outcome == TaskOutcome.FAILED
  }

  def "can suppress duplicate outputs on circular dependencies"() {
    buildFile << """
        repositories {
          mavenCentral()
        }
        
        dependencies {
          compile 'org.apache.beam:beam-runners-direct-java:2.30.0'
        }
        
        linkageChecker {
          configurations = ['compile']
        }
        """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('linkageCheck', '--stacktrace')
        .withPluginClasspath()
        .buildAndFail()

    then:
    result.output.contains("Task :linkageCheck")
    result.output.count("Circular dependency for: com.fasterxml.jackson.core:jackson-core:2.12.1") == 1
    result.output.count("Circular dependency for: com.fasterxml.jackson:jackson-bom:2.12.1") == 1
    result.task(":linkageCheck").outcome == TaskOutcome.FAILED
  }
}
