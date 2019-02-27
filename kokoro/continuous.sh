#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

export M2_HOME=/usr/local/apache-maven

# https://stackoverflow.com/questions/3459928/running-a-specific-maven-plugin-goal-from-the-command-line-in-a-sub-module-of-a/26448447#26448447
# https://stackoverflow.com/questions/11091311/maven-execjava-goal-on-a-multi-module-project

cd github/cloud-opensource-java
mvn -B clean install