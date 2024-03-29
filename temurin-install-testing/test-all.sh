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
set -eo pipefail

function wait {
  echo "$(date +%T) Sleeping 20 min"
  sleep 1200
}

terraform apply -var="enable_arm=true" -auto-approve
wait
BUCKET_FOLDER=$(terraform output --raw bucket_folder)
terraform apply -auto-approve
terraform apply -var="enable_linux=true" -var="bucket_folder=$BUCKET_FOLDER" -auto-approve
# For single invocations: terraform apply -var="enable_linux=true" -auto-approve
wait
terraform apply -auto-approve
terraform apply -var="enable_windows=true" -var="bucket_folder=$BUCKET_FOLDER" -auto-approve
# For single invocations: terraform apply -var="enable_windows=true" -auto-approve
wait
terraform apply -auto-approve
