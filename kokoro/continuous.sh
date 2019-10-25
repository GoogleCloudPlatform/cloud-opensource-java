#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

which git
which wget
which curl

cd github/cloud-opensource-java

git status
git remote -v

# M2_HOME is not used since Maven 3.5.0 https://maven.apache.org/docs/3.5.0/release-notes.html
mkdir -p ${HOME}/.m2

mvn -B clean install javadoc:jar
