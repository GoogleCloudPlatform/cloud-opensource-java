#!/bin/bash

# Fail on any error.
set -e
# Display commands to stderr.
set -x

cd github/cloud-opensource-java

# Build the project (excluding boms) to ensure validity of parent pom
# The artifact is unused in this parent-pom build.
./mvnw -V -pl 'dependencies,enforcer-rules' -Prelease -B -ntp -U verify

# copy pom with the name expected in the Maven repository
ARTIFACT_ID=$(mvn -B help:evaluate -Dexpression=project.artifactId 2>/dev/null | grep -v "^\[")
PROJECT_VERSION=$(mvn -B help:evaluate -Dexpression=project.version 2>/dev/null| grep -v "^\[")
# This build is only for parent pom. Target directory is not automatically created.
mkdir -p target
cp pom.xml target/${ARTIFACT_ID}-${PROJECT_VERSION}.pom
