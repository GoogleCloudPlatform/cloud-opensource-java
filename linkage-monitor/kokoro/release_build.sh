#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd github/cloud-opensource-java

# This generates linkage-monitor/target/linkage-monitor-X.Y.Z-all-deps.jar. The Kokoro build config
# specifies this JAR to upload to GCS.
mvn -V -B clean install
