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
  Die "Usage: ./prepare_release.sh <dependencies|bom|gradle> <release version> [<post-release-version>]"
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

if [[ "${SUFFIX}" != "dependencies" && "${SUFFIX}" != "bom" && "${SUFFIX}" != "gradle" ]]; then
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


if [[ "${SUFFIX}" = "bom" ]]; then
  cd boms/cloud-oss-bom
fi

if [[ "${SUFFIX}" = "gradle" ]]; then
  cd gradle-plugin
else
  # Updates the pom.xml with the version to release.
  mvn versions:set versions:commit -DnewVersion=${VERSION} -DgenerateBackupPoms=false
fi

# Tags a new commit for this release.
git commit -am "preparing release ${VERSION}-${SUFFIX}"
git tag v${VERSION}-${SUFFIX}

# Updates the pom.xml with the next snapshot version.
# For example, when releasing 1.5.7, the next snapshot version would be 1.5.8-SNAPSHOT.
NEXT_SNAPSHOT=${NEXT_VERSION}
if [[ "${NEXT_SNAPSHOT}" != *-SNAPSHOT ]]; then
  NEXT_SNAPSHOT=${NEXT_SNAPSHOT}-SNAPSHOT
fi

if [[ "${SUFFIX}" = "gradle" ]]; then
  # Changes the version for release and creates the commits/tags.
  echo | ./gradlew release -Prelease.releaseVersion=${VERSION} \
      ${NEXT_VERSION:+"-Prelease.newVersion=${NEXT_VERSION}"}
else
  mvn versions:set versions:commit -DnewVersion=${NEXT_SNAPSHOT} -DgenerateBackupPoms=false
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
EchoGreen 'Start the Rapid build now:'
EchoGreen 'https://rapid.corp.google.com/cloud-java-tools-cloud-opensource-java-bom-kokoro-release'
EchoGreen 'After the PR is approved and the Rapid build succeeds, you can release the library in OSSRH.'


