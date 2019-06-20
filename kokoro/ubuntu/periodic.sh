#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd github/cloud-opensource-java
# M2_HOME is not used since Maven 3.5.0 https://maven.apache.org/docs/3.5.0/release-notes.html
mvn -B clean install

# Running target of dashboard submodule
# https://stackoverflow.com/questions/3459928/running-a-specific-maven-plugin-goal-from-the-command-line-in-a-sub-module-of-a/26448447#26448447
# https://stackoverflow.com/questions/11091311/maven-execjava-goal-on-a-multi-module-project
cd dashboard

# For all versions available in Maven Central
mvn -B exec:java -Dexec.mainClass="com.google.cloud.tools.opensource.dashboard.DashboardMain" \
  -Dexec.arguments="-a com.google.cloud:libraries-bom:(0,]"

# For latest snapshot
mvn -B exec:java -Dexec.mainClass="com.google.cloud.tools.opensource.dashboard.DashboardMain" \
  -Dexec.arguments="-f ../boms/cloud-oss-bom/pom.xml"
