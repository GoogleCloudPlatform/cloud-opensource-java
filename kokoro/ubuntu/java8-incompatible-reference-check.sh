#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd github/cloud-opensource-java/dependencies
mvn -V -B clean install

mvn exec:java -e -Pjava8-incompatible-reference-check