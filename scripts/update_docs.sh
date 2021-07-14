#!/bin/bash -
# Usage: ./update_docs.sh <old version> <new version>

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

gcertstatus --quiet --check_ssh=false --check_remaining=10m \
  || Die "Run gcert."

OLDVERSION=$1
CheckVersion ${OLDVERSION}

NEWVERSION=$2
CheckVersion ${NEWVERSION}

# CITC client names can't contain periods
citcclient="bom-docs-${NEWVERSION//\./_}"

p4 g4d -f ${citcclient}
clientdir="$(p4 g4d -- "${citcclient?}")"

cd "${clientdir}"

/google/src/head/depot/google3/devtools/scripts/replace_string "&lt;version>${OLDVERSION}&lt;/version>" "&lt;version>${NEWVERSION}&lt;/version>"
/google/src/head/depot/google3/devtools/scripts/replace_string "&lt;version&gt;${OLDVERSION}&lt;/version&gt;" "&lt;version>${NEWVERSION}&lt;/version>"
/google/src/head/depot/google3/devtools/scripts/replace_string "&lt;version&gt;${OLDVERSION}&lt;/version>" "&lt;version>${NEWVERSION}&lt;/version>"


