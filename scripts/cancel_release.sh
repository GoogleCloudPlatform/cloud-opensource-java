#!/bin/bash -
# Usage: ./cancel_release.sh <release version>

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
  Die "Usage: ./cancel_release.sh <release version>]"
}

# Usage: CheckVersion <version>
CheckVersion() {
  [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+)?$ ]] || Die "Version: $1 not in ###.###.###[-XXX] format."
}

[ $# -ne 1 ] && DieUsage

EchoGreen 'Cancelling release...'

VERSION=$1
CheckVersion ${VERSION}

if [[ $(git status -uno --porcelain) ]]; then
  Die 'There are uncommitted changes.'
fi

git checkout master
git branch -D ${VERSION}-bom
git fetch --tags --force
git tag -d v${VERSION}-bom
git push origin :v${VERSION}-bom


