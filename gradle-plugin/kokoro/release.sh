#!/bin/bash

# Fail on any error.
set -e
# Display commands to stderr.
set -x

readonly PUBLISH_KEY=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_key")
readonly PUBLISH_SECRET=$(cat "${KOKORO_KEYSTORE_DIR}/72743_gradle_publish_secret")

cd github/cloud-opensource-java/gradle-plugin

./gradlew check publishPlugins \
  -Pgradle.publish.key="${PUBLISH_KEY}" \
  -Pgradle.publish.secret="${PUBLISH_SECRET}" \
  --info --stacktrace
