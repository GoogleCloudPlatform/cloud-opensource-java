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

ZONE=$(terraform output -raw zone)
PROJECT=$(terraform output -raw project)

echo ""
echo "Once connection is complete, to see startup script logs in Linux:"
echo "  sudo journalctl -u google-startup-scripts.service"
echo "To rerun startup script in Windows:"
echo '  "C:\Program Files\Google\Compute Engine\metadata_scripts\run_startup_scripts.cmd"'

gcloud compute ssh --project="$PROJECT" --zone="$ZONE" "$1"
