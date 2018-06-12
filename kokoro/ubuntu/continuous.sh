#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd github/cloud-opensource-java/dashboard
mvn exec:java -Dexec.mainClass="com.google.cloud.tools.opensource.dashboard.DashboardMain"
