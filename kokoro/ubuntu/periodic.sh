#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd github/cloud-opensource-java
# M2_HOME is not used since Maven 3.5.0 https://maven.apache.org/docs/3.5.0/release-notes.html
mvn -B clean install

# Running target of dashboard submodule
# https://stackoverflow.com/questions/3459928/running-a-specific-maven-plugin-goal-from-the-command-line-in-a-sub-module-of-a/26448447#26448447
# https://stackoverflow.com/questions/11091311/maven-execjava-goal-on-a-multi-module-project
cd dashboard

# Step 1: Generate dashboards for the released BOMs with the latest DashboardMain class
# For example: https://storage.googleapis.com/cloud-opensource-java-dashboard/com.google.cloud/libraries-bom/1.1.1/index.html

# Querying https://search.maven.org/classic/#api with
# groupId:com.google.cloud AND artifactId:libraries-bom, receiving latest (sorted) 1000 versions.
# Note: if `rows` parameter is omitted, it only returns latest 10 items.
SONATYPE_RESPONSE=`curl 'https://search.maven.org/solrsearch/select?q=g:%22com.google.cloud%22+AND+a:%22libraries-bom%22&core=gav&rows=1000&wt=json'`
# Example: '1.0.0 1.1.0'
VERSIONS=`echo $SONATYPE_RESPONSE | perl -nle 'print $1 while m/"v":"(.+?)"/g'`

if [[ -z "${VERSIONS}" ]]; then
  echo "Failed to read Sonatype API response"
  exit 1
fi

for VERSION in $VERSIONS; do
  # Generates dashboards for published BOMs.
  # Example: target/com.google.cloud/libraries-bom/1.1.1/index.html
  mvn -B exec:java -Dexec.mainClass="com.google.cloud.tools.opensource.dashboard.DashboardMain" \
    -Dexec.arguments="-c com.google.cloud:libraries-bom:${VERSION}"
done

# Step 2: Generate dashboard at target/com.google.cloud:libraries-bom/snapshot/index.html
# Kokoro uploads this content to
# https://storage.googleapis.com/cloud-opensource-java-dashboard/com.google.cloud/libraries-bom/snapshot/index.html
mvn -B exec:java -Dexec.mainClass="com.google.cloud.tools.opensource.dashboard.DashboardMain" \
  -Dexec.arguments="-f ../boms/cloud-oss-bom/pom.xml"
