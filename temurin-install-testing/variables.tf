variable "user_email" {
  type        = string
  description = "Current user email"
}
variable "project_id" {
  type        = string
  description = "GCP Project ID of the project being used"
}
variable "bucket" {
  type        = string
  description = "Result storage bucket"
}

variable "enable_arm" {
  type        = bool
  description = "If true, perform arm tests"
  default     = false
}
variable "enable_windows" {
  type        = bool
  description = "If true, perform x86 Windows tests"
  default     = false
}
variable "enable_linux" {
  type        = bool
  description = "If true, perform x86 Linux tests"
  default     = false
}

variable "location" {
  type        = string
  description = "Bucket location used for GCS"
  default     = "US-CENTRAL1"
}
variable "region" {
  type        = string
  description = "GCP region used to deploy resources"
  default     = "us-central1"
}
variable "zone" {
  type        = string
  description = "GCP zone used to deploy resources. Must be a zone in the chosen region."
  default     = "us-central1-a"
}
variable "bucket_folder" {
  type        = string
  description = "Generally, leave empty. By default, a timestamp will be provided as the value."
  default     = ""
}


## See https://cloud.google.com/compute/docs/machine-resource
variable "x86_machine_types" {
  type        = list(string)
  description = "GCE machine types to create instances of"
  default     = [
    #"c3-highcpu-4",
    #"n2-standard-96",
    ##"m3-ultramem-32", # Requires special quota
    #"n2-standard-2",
    #"c2-standard-4",
    ##"m2-ultramem-208", # Requires special quota
    #"a2-highgpu-1g",
    ##"m1-megamem-96", # Requires special quota
    ##"m1-ultramem-40", # Requires special quota
    "n1-standard-1",
    #"t2d-standard-1",
    #"n2d-standard-2",
  ]
}

# See https://cloud.google.com/compute/docs/images
variable "x86_boot_images" {
  type        = list(string)
  description = "GCE boot image to use with created instance"
  default     = [
    "debian-cloud/debian-11",
    "debian-cloud/debian-10",

    "rhel-cloud/rhel-9",
    "rhel-cloud/rhel-7",
    "rhel-sap-cloud/rhel-9-0-sap-ha",
    "rhel-sap-cloud/rhel-7-7-sap-ha",

    "centos-cloud/centos-stream-9",
    "centos-cloud/centos-stream-8",
    "centos-cloud/centos-7",

    "rocky-linux-cloud/rocky-linux-9-optimized-gcp",
    "rocky-linux-cloud/rocky-linux-8-optimized-gcp",
    "rocky-linux-cloud/rocky-linux-8",

    "ubuntu-os-cloud/ubuntu-2204-lts",
    "ubuntu-os-cloud/ubuntu-2004-lts",
    "ubuntu-os-pro-cloud/ubuntu-pro-2204-lts",
    "ubuntu-os-pro-cloud/ubuntu-pro-1804-lts",
    "ubuntu-os-pro-cloud/ubuntu-pro-1604-lts",

    "suse-cloud/sles-15",
    "suse-byos-cloud/sles-12-byos",
    "suse-sap-cloud/sles-12-sp5-sap",
  ]
}


# See https://cloud.google.com/compute/docs/images
variable "x86_windows_boot_images" {
  type        = list(string)
  description = "GCE Windows boot image to use with created instance"
  default     = [
    "windows-cloud/windows-2022",
    "windows-cloud/windows-2022-core",
    "windows-cloud/windows-2012-r2",
    "windows-cloud/windows-2012-r2-core",

    "windows-sql-cloud/sql-web-2022-win-2022",
    "windows-sql-cloud/sql-std-2022-win-2022",
    "windows-sql-cloud/sql-ent-2022-win-2022",

    "windows-sql-cloud/sql-web-2022-win-2019",
    "windows-sql-cloud/sql-std-2022-win-2019",
    "windows-sql-cloud/sql-ent-2022-win-2019",

    "windows-sql-cloud/sql-web-2014-win-2012-r2",
    "windows-sql-cloud/sql-std-2014-win-2012-r2",
    "windows-sql-cloud/sql-ent-2014-win-2012-r2",
  ]
}

## See https://cloud.google.com/compute/docs/machine-resource
variable "arm_machine_types" {
  type        = list(string)
  description = "GCE machine types to create instances of"
  default     = [
    "t2a-standard-1"
  ]
}

# See https://cloud.google.com/compute/docs/images
variable "arm_boot_images" {
  type        = list(string)
  description = "GCE boot image to use with created instance"
  default     = [
    "debian-cloud/debian-11-arm64",
    "rhel-cloud/rhel-9-arm64",
    "rocky-linux-cloud/rocky-linux-9-arm64",
    "rocky-linux-cloud/rocky-linux-9-optimized-gcp-arm64",
    "rocky-linux-cloud/rocky-linux-8-optimized-gcp-arm64",
    "suse-cloud/sles-15-arm64",
    "ubuntu-os-cloud/ubuntu-2204-lts-arm64",
    "ubuntu-os-cloud/ubuntu-2004-lts-arm64",
  ]
}
