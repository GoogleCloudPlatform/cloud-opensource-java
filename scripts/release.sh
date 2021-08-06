#!/bin/bash -
# Usage: ./release.sh <dependencies|bom|lts> <release version>

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
  Die "Usage: ./release.sh <dependencies|bom|lts> <release version> [<post-release-version>]"
}

# Usage: CheckVersion <version>
CheckVersion() {
  [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+)?$ ]] || Die "Version: $1 not in ###.###.###[-XXX] format."
}

# Usage: IncrementVersion <version>
IncrementVersion() {
  local version=$1
  local minorVersion=$(echo $version | sed 's/[0-9]\+\.[0-9]\+\.\([0-9]\+\)*/\1/')
  local nextMinorVersion=$((minorVersion+1))
  echo $version | sed "s/\([0-9]\+\.[0-9]\+\)\.[0-9]\+/\1.$nextMinorVersion/"
}

[ $# -ne 2 ] && [ $# -ne 3 ] && DieUsage

EchoGreen '===== RELEASE SETUP SCRIPT ====='

gcertstatus --quiet --check_ssh=false --check_remaining=10m \
  || Die "Run gcert."

SUFFIX=$1

if [[ "${SUFFIX}" != "dependencies" && "${SUFFIX}" != "bom" && "${SUFFIX}" != "lts" ]]; then
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

if [[ "${SUFFIX}" = "lts" && "${VERSION}" != *.*.0 ]]; then
  # LTS patch release (the patch version is non-zero) is based on N.0.0-lts branch, where N is the
  # major release number. (Note that the minor version part of this BOM is always zero)
  BASE_VERSION=$(echo $VERSION | sed 's/\([0-9]\+\.[0-9]\+\)\.[0-9]\+/\1.0/')
  # For example, LTS BOM patch release 5.0.3 would create "5.0.3-lts" branch based on the base
  # branch "5.0.0-lts". For the details of a patch release, see boms/cloud-lts-bom/RELEASING.md.
  BASE_BRANCH=${BASE_VERSION}-lts
else
  # Make sure client is up to date with the latest changes.
  BASE_BRANCH=master
fi
echo "BASE_BRANCH: ${BASE_BRANCH}"
git checkout ${BASE_BRANCH}
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
elif [[ "${SUFFIX}" = "lts" ]]; then
  cd boms/cloud-lts-bom
fi

# Updates the pom.xml with the version to release.
mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=${VERSION} -DgenerateBackupPoms=false

if [[ "${SUFFIX}" = "dependencies" ]]; then
  sed -i -e "s/version = .*/version = ${VERSION}/" gradle-plugin/gradle.properties
  sed -i -e "s/linkage-monitor-.\+-all-deps/linkage-monitor-${VERSION}-all-deps/" linkage-monitor/action.yml
fi

# Tags a new commit for this release.
git commit -am "preparing release ${VERSION}-${SUFFIX}"
RELEASE_TAG="v${VERSION}-${SUFFIX}"
git tag "${RELEASE_TAG}"
mvn org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion=${NEXT_SNAPSHOT} -DgenerateBackupPoms=false

if [[ "${SUFFIX}" = "dependencies" ]]; then
  sed -i -e "s/version = .*/version = ${NEXT_SNAPSHOT}/" gradle-plugin/gradle.properties
  sed -i -e "s/linkage-monitor-.\+-all-deps/linkage-monitor-${NEXT_SNAPSHOT}-all-deps/" linkage-monitor/action.yml
fi

# Commits this next snapshot version.
git commit -am "${NEXT_SNAPSHOT}"

# Pushes the tag and release branch to Github.
git push origin "${RELEASE_TAG}"

# If the suffix is "lts", then the branch is protected. In that case any subsequent modification is
# blocked without an approved pull request.
git push --set-upstream origin ${VERSION}-${SUFFIX}

# Create the PR
gh pr create --title "Release ${VERSION}-${SUFFIX}" \
    --body "Release ${VERSION}-${SUFFIX}" \
    --base ${BASE_BRANCH}

# File a PR on Github for the new branch. Have someone LGTM it, which gives you permission to continue.
EchoGreen 'Ask someone to approve this PR.'

# CITC client names can't contain periods
citcclient="release-${VERSION//\./_}-${SUFFIX}"

p4 g4d -f ${citcclient}
clientdir="$(p4 g4d -- "${citcclient?}")"

cd "${clientdir}"

RELEASE_RAPID_PROJECT=java/com/google/cloud/java/tools:ReleaseRapidProject
blaze build "${RELEASE_RAPID_PROJECT}"

release_rapid_project() {
  local project="$1"
  "blaze-bin/${RELEASE_RAPID_PROJECT/://}" \
      --project_name="cloud-java-tools-cloud-opensource-java-${project}-release" \
      --committish="${RELEASE_TAG}"
}

if [[ "${SUFFIX}" = "bom" ]]; then
  release_rapid_project bom-kokoro
elif [[ "${SUFFIX}" = "lts" ]]; then
  release_rapid_project lts-kokoro
else
  # Run the Rapid projects concurrently
  release_rapid_project parent-kokoro &
  release_rapid_project dependencies-kokoro &
  release_rapid_project enforcer-rules &
  release_rapid_project gradle-plugin-kokoro &
  release_rapid_project linkage-monitor-kokoro &
  wait
fi

if [[ "${SUFFIX}" = "dependencies" ]]; then
  LINKAGE_MONITOR_JAR_URL="https://storage.googleapis.com/cloud-opensource-java-linkage-monitor/linkage-monitor-${VERSION}-all-deps.jar"
  curl --fail --output /dev/null $LINKAGE_MONITOR_JAR_URL
  if [ "$?" == "0" ]; then
    EchoGreen "Linkage Monitor uber JAR is available."
    echo "Once the pull request is approved, update the v1-linkagemonitor tag:"
    echo "$ git tag -d v1-linkagemonitor"
    echo "$ git tag -a v1-linkagemonitor ${RELEASE_TAG} -m \"Linkage Monitor release on ${RELEASE_TAG}\""
    echo "$ git push -f origin v1-linkagemonitor"
  else
    EchoRed "Couldn't confirm the new uber JAR at ${LINKAGE_MONITOR_JAR_URL}"
  fi
fi

# TODO print instructions for releasing from Sonatype OSSRH to Maven Central when
# ReleaseRapidProject succeeds. Eventually we should automate this step too.
