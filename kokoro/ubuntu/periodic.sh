#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x



# https://stackoverflow.com/questions/3459928/running-a-specific-maven-plugin-goal-from-the-command-line-in-a-sub-module-of-a/26448447#26448447
# https://stackoverflow.com/questions/11091311/maven-execjava-goal-on-a-multi-module-project

cd github/cloud-opensource-java
# M2_HOME is not used since Maven 3.5.0 https://maven.apache.org/docs/3.5.0/release-notes.html
mvn -B clean install

cd dashboard
mvn -B exec:java -Dexec.mainClass="com.google.cloud.tools.opensource.dashboard.DashboardMain"
