#!/bin/bash -
# Usage: ./release.sh <dependencies|bom> <release version>

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
  Die "Usage: ./release.sh <dependencies|bom> <release version> [<post-release-version>]"
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

gcertstatus --quiet --check_ssh=false --check_remaining=10m \
  || Die "Run gcert."

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

blaze build java/com/google/cloud/java/tools:ReleaseRapidProject
if [[ "${SUFFIX}" = "bom" ]]; then
  blaze-bin/java/com/google/cloud/java/tools/ReleaseRapidProject \
      --project_name=cloud-java-tools-cloud-opensource-java-bom-kokoro-release \
      --version=${VERSION} --committish_suffix=${SUFFIX}
else
  # Run the Rapid projects concurrently
  blaze-bin/java/com/google/cloud/java/tools/ReleaseRapidProject \
    --project_name=cloud-java-tools-cloud-opensource-java-parent-kokoro-release \
    --version=${VERSION} --committish_suffix=${SUFFIX} &
  blaze-bin/java/com/google/cloud/java/tools/ReleaseRapidProject \
    --project_name=cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release \
    --version=${VERSION} --committish_suffix=${SUFFIX} &
  blaze-bin/java/com/google/cloud/java/tools/ReleaseRapidProject \
    --project_name=cloud-java-tools-cloud-opensource-java-enforcer-rules-release \
    --version=${VERSION} --committish_suffix=${SUFFIX} &
  blaze-bin/java/com/google/cloud/java/tools/ReleaseRapidProject \
    --project_name=cloud-java-tools-cloud-opensource-java-gradle-plugin-kokoro-release \
    --version=${VERSION} --committish_suffix=${SUFFIX} &
  wait
fi

# TODO print instructions for releasing from Sonatype OSSRH to Maven Central when
# ReleaseRapidProject succeeds. Eventually we should automate this step too.