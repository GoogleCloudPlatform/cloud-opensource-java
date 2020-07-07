#!/bin/bash

set -o errexit
set -o xtrace

mkdir -p $HOME/.gradle
readonly HOME_GRADLE_PROPERTY="$HOME/.gradle/gradle.properties"

# Recommended way to store API key in
# https://guides.gradle.org/publishing-plugins-to-gradle-plugin-portal/
# and go/yaqs/5342701566951424 for ISE check.
echo -n 'gradle.publish.key=' >> $HOME_GRADLE_PROPERTY
cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_key" >> $HOME_GRADLE_PROPERTY
echo -n 'gradle.publish.secret=' >> $HOME_GRADLE_PROPERTY
cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_secret" >> $HOME_GRADLE_PROPERTY

cd github/cloud-opensource-java/gradle-plugin

./gradlew build

./gradlew publishPlugins --info --stacktrace
