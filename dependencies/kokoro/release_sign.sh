#!/bin/bash

# Fail on any error.
set -e
# Display commands being run.
set -x

cd $KOKORO_GFILE_DIR
mkdir signed && chmod 777 signed

# find the latest directory under prod/cloud-opensource-java/dependencies/gcp_ubuntu/release-build/
# This directory is an alias to Google Cloud Storage
LAST_BUILD=$(ls prod/cloud-opensource-java/dependencies/gcp_ubuntu/release-build/ | sort -rV | head -1)

echo "Signing files in " `pwd`/prod/cloud-opensource-java/dependencies/gcp_ubuntu/release-build/${LAST_BUILD}

# find the jars and the pom in the latest build artifact directory
FILES=$(find `pwd`/prod/cloud-opensource-java/dependencies/gcp_ubuntu/release-build/${LAST_BUILD}/* -type f \( -iname \*.jar -o -iname \*.pom \))

for f in $FILES
do
  echo "Processing $f file..."
  filename=$(basename "$f")
  mv $f signed/$filename
  if /escalated_sign/escalated_sign.py -j /escalated_sign_jobs -t linux_gpg_sign \
    `pwd`/signed/$filename
  then echo "Signed $filename"
  else
    echo "Could not sign $filename"
    exit 1
  fi
done

# bundle the artifacts for manual deploy to the Maven staging repository
cd signed
POM_NAME=$(ls *.pom)
BUNDLE_NAME=${POM_NAME%.pom}-bundle.jar
jar -cvf ${BUNDLE_NAME} *
