#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

if which brew > /dev/null; then
  # Kokoro MacOS environment does not have Java 11
  brew install --cask adoptopenjdk
fi

export MAVEN_OPTS="-Xmx8g"

cd github/cloud-opensource-java

./mvnw -V -B -ntp clean install javadoc:jar

cd gradle-plugin
./gradlew build publishToMavenLocal
