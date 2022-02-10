#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd github/cloud-opensource-java
# No need to run tests
./mvnw -V -B -ntp clean install -DskipTests -Denforcer.skip -Dinvoker.skip

# Running target of dashboard submodule
# https://stackoverflow.com/questions/3459928/running-a-specific-maven-plugin-goal-from-the-command-line-in-a-sub-module-of-a/26448447#26448447
# https://stackoverflow.com/questions/11091311/maven-execjava-goal-on-a-multi-module-project
cd dashboard

# For all versions available in Maven Central and local repository
../mvnw -V -B -ntp exec:java -Dexec.mainClass="com.google.cloud.tools.opensource.dashboard.DashboardMain" \
  -Dexec.arguments="-a com.google.cloud:libraries-bom"

../mvnw -V -B -ntp exec:java -Dexec.mainClass="com.google.cloud.tools.opensource.dashboard.DashboardMain" \
  -Dexec.arguments="-a com.google.cloud:gcp-lts-bom"
