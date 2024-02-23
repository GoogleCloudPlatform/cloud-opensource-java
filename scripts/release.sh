#!/bin/bash -
# Usage: ./release.sh <dependencies|bom|lts> <release version>

set -e

echo_red() {
  echo "$(tput setaf 1; tput bold)$1$(tput sgr0)"
}

echo_green() {
  echo "$(tput setaf 2; tput bold)$1$(tput sgr0)"
}

die() {
  echo_red "$1"
  exit 1
}

die_usage() {
  die "Usage: ./release.sh <dependencies|bom|lts> <release version> [<post-release-version>]"
}

# Check if the version is in ###.###.###[-XXX] format
check_version() {
  [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+)?$ ]] || die "Version: $1 not in ###.###.###[-XXX] format."
}

# Increment the patch version
increment_version() {
  local version=$1
  local next_patch_version=$(echo $version | sed 's/\([0-9]\+\.[0-9]\+\.\)[0-9]\+/\1/')
  echo $version | sed "s/\([0-9]\+\.[0-9]\+\.\)[0-9]\+/\1$((next_patch_version+1))/"
}

[ $# -ne 2 ] && [ $# -ne 3 ] && die_usage

echo_green '===== RELEASE SETUP SCRIPT ====='
