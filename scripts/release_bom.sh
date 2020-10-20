#!/bin/bash -
# Usage: ./prepare_release.sh <dependencies|bom> <release version>

set -e

EchoRed() {
  echo "$(tput setaf 1; tput bold)$1$(tput sgr0)"
}
EchoGreen() {
  echo "$(tput setaf 2; tput bold)$1$(tput sgr0)"
}

Die() {
  EchoRed "$1"
  exit 1
}

DieUsage() {
  Die "Usage: ./prepare_release.sh <dependencies|bom> <release version> [<post-release-version>]"
}

# Usage: CheckVersion <version>
CheckVersion() {
  [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+)?$ ]] || Die "Version: $1 not in ###.###.###[-XXX] format."
}

# Usage: IncrementVersion <version>
IncrementVersion() {
  local version=$1
  local minorVersion=$(echo $version | sed 's/[0-9][0-9]*\.[0-9][0-9]*\.\([0-9][0-9]\)*/\1/')
  local nextMinorVersion=$((minorVersion+1))
  echo $version | sed "s/\([0-9][0-9]*\.[0-9][0-9]*\)\.[0-9][0-9]*/\1.$nextMinorVersion/"
}

[ $# -ne 2 ] && [ $# -ne 3 ] && DieUsage

EchoGreen '===== RELEASE SETUP SCRIPT ====='

SUFFIX=$1

if [[ "${SUFFIX}" != "dependencies" && "${SUFFIX}" != "bom" ]]; then
  DieUsage
fi

VERSION=$2
CheckVersion ${VERSION}

if [ -n "$3" ]; then
  NEXT_VERSION=$3
  CheckVersion ${NEXT_VERSION}
else
  NEXT_VERSION=$(IncrementVersion $VERSION)
  CheckVersion ${NEXT_VERSION}
fi

if [[ $(git status -uno --porcelain) ]]; then
  Die 'There are uncommitted changes.'
fi

# Make sure client is up to date with the latest changes.
git checkout master
git pull

# Checks out a new branch for this version release (eg. 1.5.7).
git checkout -b ${VERSION}-${SUFFIX}


# Updates the pom.xml with the next snapshot version.
# For example, when releasing 1.5.7, the next snapshot version would be 1.5.8-SNAPSHOT.
NEXT_SNAPSHOT=${NEXT_VERSION}
if [[ "${NEXT_SNAPSHOT}" != *-SNAPSHOT ]]; then
  NEXT_SNAPSHOT=${NEXT_SNAPSHOT}-SNAPSHOT
fi

if [[ "${SUFFIX}" = "bom" ]]; then
  cd boms/cloud-oss-bom
fi

# Updates the pom.xml with the version to release.
mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=${VERSION} -DgenerateBackupPoms=false

if [[ "${SUFFIX}" = "dependencies" ]]; then
  sed -i "" "s/version = .*/version = ${VERSION}/" gradle-plugin/gradle.properties
fi

# Tags a new commit for this release.
git commit -am "preparing release ${VERSION}-${SUFFIX}"
git tag v${VERSION}-${SUFFIX}
mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=${NEXT_SNAPSHOT} -DgenerateBackupPoms=false

if [[ "${SUFFIX}" = "dependencies" ]]; then
  sed -i "" "s/version = .*/version = ${NEXT_SNAPSHOT}/" gradle-plugin/gradle.properties
fi

# Commits this next snapshot version.
git commit -am "${NEXT_SNAPSHOT}"

# Pushes the tag and release branch to Github.
git push origin v${VERSION}-${SUFFIX}
git push --set-upstream origin ${VERSION}-${SUFFIX}

# Create the PR
gh pr create -t "Release ${VERSION}-${SUFFIX}" -b "Release ${VERSION}-${SUFFIX}"

# File a PR on Github for the new branch. Have someone LGTM it, which gives you permission to continue.
EchoGreen 'Ask someone to approve this PR.'

# CITC client names can't contain periods
citcclient="release-${VERSION//\./_}-${SUFFIX}"

p4 g4d -f ${citcclient}
clientdir="$(p4 g4d -- "${citcclient?}")"

cd "${clientdir}"

blaze run java/com/google/cloud/java/tools:ReleaseBom -- --version=${VERSION}

# TODO check status of ReleaseBom and die with instructions if it failed. Otherwise

# TODO print URL of Rapid Web UI for this build in case user wants to
# follow along; possibly this should be done in the Java program ReleaseBom

# TODO print instructions for releasing from Sonatype OSSRH to Maven Central
# Eventually we should automate this step too.

