#!/bin/bash

# Fail on any error.
set -e
# Display commands to stderr.
set -x

cd github/cloud-opensource-java
# 'Enforcer-rules' needs to be built with 'dependencies' (without 'boms')
mvn -V -pl 'dependencies,enforcer-rules' -Prelease -B -U verify
cd enforcer-rules

# copy pom with the name expected in the Maven repository
ARTIFACT_ID=$(mvn -B help:evaluate -Dexpression=project.artifactId 2>/dev/null | grep -v "^\[")
PROJECT_VERSION=$(mvn -B help:evaluate -Dexpression=project.version 2>/dev/null| grep -v "^\[")
cp pom.xml target/${ARTIFACT_ID}-${PROJECT_VERSION}.pom
