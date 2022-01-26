#!/bin/bash

# e.g., com.google.protobuf:protobuf-java:3.18.2,com.google.protobuf:protobuf-bom:3.18.2
MAVEN_COORDINATES_LIST=$1

set -e

IFS_BACKUP=$IFS

function replacePomFile () {
  GROUP_ID=$1
  ARTIFACT_ID=$2
  VERSION=$3
  echo "$GROUP_ID $ARTIFACT_ID $VERSION"

  while IFS= read -r -d '' POM
  do
    HAS_DEPENDENCY=$(xmlstarlet sel -N x=http://maven.apache.org/POM/4.0.0 \
        -t -v "count(//x:project/x:dependencies/x:dependency/x:artifactId[text()='${ARTIFACT_ID}'])" \
        $POM)
    if [ "${HAS_DEPENDENCY}" -eq 0 ]; then
      continue;
    fi

    C=$(xmlstarlet sel -N x=http://maven.apache.org/POM/4.0.0 \
        -t -v "count(//x:project/x:dependencies/x:dependency/x:artifactId[text()='${ARTIFACT_ID}']/../x:version)" \
        "$POM")
    echo "$POM" has $C matching node
    if [ "$C" -eq 0 ];then
      xmlstarlet ed --pf --inplace -N x=http://maven.apache.org/POM/4.0.0 \
        --append "//*/x:artifactId[text()='${ARTIFACT_ID}']" \
        -t elem -n version --value "$VERSION" "$POM"
    else
      xmlstarlet ed --pf --inplace -N x=http://maven.apache.org/POM/4.0.0 \
        --update "//*/x:artifactId[text()='${ARTIFACT_ID}']/../x:version" \
        --value "$VERSION" "$POM"
    fi
  done <   <(find . -name pom.xml -print0)
}

IFS_BACKUP=$IFS
IFS=","
for MAVEN_COORDINATES in $MAVEN_COORDINATES_LIST; do
  IFS=':' read -ra items <<< "$MAVEN_COORDINATES"
  GROUP_ID="${items[0]}"
  ARTIFACT_ID="${items[1]}"
  VERSION="${items[2]}"
  replacePomFile "$GROUP_ID" "$ARTIFACT_ID" "$VERSION"
done

