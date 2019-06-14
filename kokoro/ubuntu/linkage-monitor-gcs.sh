#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd github/cloud-opensource-java
mvn -B clean install

cd linkage-monitor/target
mv linkage-monitor-*-all-deps.jar linkage-monitor-latest-all-deps.jar
