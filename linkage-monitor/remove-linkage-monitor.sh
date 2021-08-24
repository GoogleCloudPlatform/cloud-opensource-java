#!/bin/sh

# Usage
# Run this script from within an empty directory to run this script. Run it with repository name,
# such as "java-spanner". This script performs the following steps:
# - Clones the specified the repository under current directory as remove-linkage-monitor-from-required
#   branch.
# - Modifies .github/sync-repo-settings.yaml to remove lines that has linkage-monitor
# - Commits the change and creates a pull request.

#
# Copyright 2021 Google LLC.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

gh config set prompt disabled

CLIENT=$1

GITHUB_USER_NAME=suztomo

function printUsage() {
  echo "You need to specify repository name. E.g., $0 java-firestore"
}

if [ -z "${CLIENT}" ]; then
  printUsage
  exit 1
fi

set -e
if [ ! -d "${CLIENT}" ]; then
  gh repo clone "googleapis/${CLIENT}"
fi

cd $CLIENT

BRANCH=remove-linkage-monitor-from-required
git checkout -b ${BRANCH} origin/master

# What if the fork already exists? It just prints "suztomo/java-firestore already exists" with
# successful status code.
gh repo fork --remote "googleapis/${CLIENT}" --remote-name ${GITHUB_USER_NAME}
git remote add ${GITHUB_USER_NAME} "https://github.com/${GITHUB_USER_NAME}/${CLIENT}"

echo "Sleeping 3 seconds to wait for the fork to be ready"
sleep 3
# This is sed syntax for Mac OS
sed -i '' '/linkage-monitor/d' .github/sync-repo-settings.yaml

git commit .github/sync-repo-settings.yaml  -m 'ci: removing linkage-monitor from required checks'

# git config --global credential.helper 'cache --timeout=28800'
git push --set-upstream ${GITHUB_USER_NAME} $BRANCH

gh pr create --base master --title 'ci: removing linkage-monitor from the required checks' \
  --body 'Linkage Monitor is no longer needed, because the Libraries BOM synchronizes with Google Cloud BOM and the shared dependencies BOM https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/2137' \
  --head "${GITHUB_USER_NAME}:${BRANCH}"
