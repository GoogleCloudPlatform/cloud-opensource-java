#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

export MAVEN_OPTS="-Xmx8g"

cd github/cloud-opensource-java

./mvnw -V -B -ntp clean install javadoc:jar

cd gradle-plugin
./gradlew build publishToMavenLocal
