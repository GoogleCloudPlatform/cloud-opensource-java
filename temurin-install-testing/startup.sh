#!/bin/bash

#
# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

function prepare_installer_debian_ubuntu {
  sudo mkdir -p /etc/apt/keyrings
  sudo wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public |
    sudo tee /etc/apt/keyrings/adoptium.asc

  eval "$(grep VERSION_CODENAME /etc/os-release)"
  sudo tee /etc/apt/sources.list.d/adoptium2.list <<EOM
deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $VERSION_CODENAME main
EOM

  export INSTALLER="apt"
}

function prepare_installer_redhat_centos_rocky {
  eval "$(grep VERSION_ID /etc/os-release)"
  eval "$(grep ^ID= /etc/os-release)"

  # Get only the major version by splitting on '.'
  OLD_IFS=$IFS
  IFS='.'
  read -ra split_version <<<"$VERSION_ID"
  IFS=$OLD_IFS
  MAJOR_VERSION=$${split_version[0]}

  sudo tee /etc/yum.repos.d/adoptium.repo <<EOM
[Adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/$ID/$MAJOR_VERSION/\$basearch
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
EOM

  export INSTALLER="yum"
}

function prepare_installer_sles {
  sudo mkdir -p /etc/zypp/keyrings
  sudo wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public |
    sudo tee /etc/zypp/keyrings/adoptium.asc
  sudo rpm --import /etc/zypp/keyrings/adoptium.asc

  eval "$(grep VERSION_ID /etc/os-release)"
  sudo zypper ar -f "https://packages.adoptium.net/artifactory/rpm/opensuse/$VERSION_ID/$(uname -m)" adoptium

  export INSTALLER="zypper"

  ## START: For reporting purposes only - not required for Java installation.
  wget -O - https://packages.cloud.google.com/yum/doc/yum-key.gpg |
    tee /etc/zypp/keyrings/google-yum.asc
  rpm --import /etc/zypp/keyrings/google-yum.asc

  zypper ar -f "https://packages.cloud.google.com/yum/repos/cloud-sdk-el7-$(uname -m)" google
  zypper install -y google-cloud-sdk
}

function prepare_installer {
  if [[ ${vm_name} == *"debian"* ]] || [[ ${vm_name} == *"ubuntu"* ]]; then
    prepare_installer_debian_ubuntu
  elif [[ ${vm_name} == *"rhel"* ]] || [[ ${vm_name} == *"centos"* ]] || [[ ${vm_name} == *"rocky"* ]]; then
    prepare_installer_redhat_centos_rocky
  elif [[ ${vm_name} == *"sles"* ]]; then
    prepare_installer_sles
  else
    echo "Unsupported OS."
    exit 1
  fi
}

function perform_test {
  if [ -z "$1" ]; then
    echo "perform_test argument must provide java package to install"
    exit 1
  fi

  SUCCESS_FILE="${os_name}-$1.txt"
  ERROR_FILE="${os_name}-$1-error.txt"
  FILE=$SUCCESS_FILE

  if java -h &>/dev/null; then
    echo "Java already installed."
    exit 1
  fi

  $INSTALLER update -y
  if ! $INSTALLER install -y "$1" 2>"result.txt"; then
    FILE=$ERROR_FILE
  else
    # java -version uses the error stream
    if ! java -version 2>"result.txt"; then
      FILE=$ERROR_FILE
    fi
  fi
  cat "result.txt" >"$FILE"
  gcloud storage cp "$FILE" "gs://${bucket}/${bucket_folder}/${machine_type}/"
  $INSTALLER remove -y "$1"
}

prepare_installer
perform_test temurin-8-jdk
perform_test temurin-11-jdk
perform_test temurin-17-jdk
perform_test temurin-19-jdk
perform_test temurin-20-jdk

# Store the VM's console logs
gcloud compute instances get-serial-port-output "${vm_name}" --zone "${vm_zone}" >"${vm_name}.txt"
gcloud storage cp "${vm_name}.txt" "gs://${bucket}/${bucket_folder}/logs/${vm_name}.txt"
