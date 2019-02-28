#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

export M2_HOME=/usr/local/apache-maven

cd github/cloud-opensource-java
mvn -B clean install
