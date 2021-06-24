#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd github/cloud-opensource-java

./mvnw -V -B -ntp clean install javadoc:jar

cd gradle-plugin
./gradlew build publishToMavenLocal
