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

function Refresh-Path
{
    $MachinePath = [System.Environment]::GetEnvironmentVariable('Path', 'Machine')
    $UserPath = [System.Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = "$MachinePath;$UserPath"
}

function Perform-Test
{
    param (
        $JdkVersion
    )
    $BaseFileName = "${os_name}-jdk$JdkVersion"
    $SuccessFileName = "$BaseFileName.txt"
    $ErrorFileName = "$BaseFileName-error.txt"
    $OriginalPath = $env:Path

    try
    {
        # [START temurin_installation_windows]
        $JdkUrl = "https://api.adoptium.net/v3/binary/latest/$JdkVersion/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
        $JdkExtractionPath = "C:\temurin-$JdkVersion-jdk"
        $JdkDownload = "$JdkExtractionPath.zip"

        "Downloading: $JdkUrl"
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]'Tls12'
        Invoke-WebRequest -Uri $JdkUrl -OutFile $JdkDownload
        Expand-Archive $JdkDownload -DestinationPath $JdkExtractionPath -Force

        pushd $JdkExtractionPath
        $JdkPath = (Get-ChildItem).FullName
        popd

        [System.Environment]::SetEnvironmentVariable('JAVA_HOME', $JdkPath, 'Machine')
        [System.Environment]::SetEnvironmentVariable('Path', "$env:Path;$JdkPath\bin", 'Machine')
        # [END temurin_installation_windows]

        Refresh-Path
        java -version 2>&1 | %%{ "$_" } > $SuccessFileName # For syntax, see https://stackoverflow.com/a/20950421

        # Reset for next test
        [System.Environment]::SetEnvironmentVariable('JAVA_HOME', '', 'Machine')
        [System.Environment]::SetEnvironmentVariable('Path', $OriginalPath, 'Machine')
        Refresh-Path
        try
        {
            java -version # Expect failure.

            "Java not fully uninstalled from Path: $env:Path" | Out-File -FilePath $ErrorFileName
            gcloud storage cp $ErrorFileName "gs://${bucket}/${bucket_folder}/${machine_type}/"
            exit 1
        }
        catch
        {
            # Expected. Successfully removed from path.
            gcloud storage cp $SuccessFileName "gs://${bucket}/${bucket_folder}/${machine_type}/"
        }
    }
    catch
    {
        Write-Output $_
        Write-Output $_.ScriptStackTrace

        "Error. See VM serial port 1 logs for details." | Out-File -FilePath $ErrorFileName
        gcloud storage cp $ErrorFileName "gs://${bucket}/${bucket_folder}/${machine_type}/"
    }
}

Perform-Test -JdkVersion 8
Perform-Test -JdkVersion 11
Perform-Test -JdkVersion 17
Perform-Test -JdkVersion 19
Perform-Test -JdkVersion 20

# Store the VM's console logs
gcloud compute instances get-serial-port-output "${vm_name}" --zone "${vm_zone}" > "${vm_name}.txt"
gcloud storage cp "${vm_name}.txt" "gs://${bucket}/${bucket_folder}/logs/${vm_name}.txt"
"Done with JDK testing."
