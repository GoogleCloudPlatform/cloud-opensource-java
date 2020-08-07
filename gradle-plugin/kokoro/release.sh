#!/bin/bash

set -o errexit
set -o xtrace

mkdir -p $HOME/.gradle
export GRADLE_USER_HOME="$HOME/.gradle"
readonly HOME_GRADLE_PROPERTY="$GRADLE_USER_HOME/gradle.properties"

# Recommended way to store API key in
# https://guides.gradle.org/publishing-plugins-to-gradle-plugin-portal/
# and go/yaqs/5342701566951424 for ISE check.
echo -n 'gradle.publish.key=' >> $HOME_GRADLE_PROPERTY
cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_key" >> $HOME_GRADLE_PROPERTY
echo >> $HOME_GRADLE_PROPERTY
echo -n 'gradle.publish.secret=' >> $HOME_GRADLE_PROPERTY
cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_secret" >> $HOME_GRADLE_PROPERTY

cd github/cloud-opensource-java/gradle-plugin

./gradlew build

./gradlew publishPlugins --info --stacktrace
