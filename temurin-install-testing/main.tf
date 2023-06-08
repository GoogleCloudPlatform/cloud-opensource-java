terraform {
  required_providers {
    google = {
      source = "hashicorp/google"
    }
  }
}
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}
resource "google_project_service" "compute" {
  service            = "compute.googleapis.com"
  disable_on_destroy = false
}
resource "google_project_service" "storage" {
  service            = "storage.googleapis.com"
  disable_on_destroy = false
}

resource "google_service_account" "default" {
  account_id   = "temurin-service-account"
  display_name = "Service Account"
}


locals {
  x86_instances = var.enable_linux ? setproduct(var.x86_machine_types, var.x86_boot_images) : []
  arm_instances = var.enable_arm ? setproduct(var.arm_machine_types, var.arm_boot_images) : []

  linux_instances = [
  for entry in concat(local.x86_instances, local.arm_instances) : {
    name    = "${entry[0]}-${split("/", entry[1])[1]}"
    type    = entry[0]
    image   = entry[1]
    os_name = split("/", entry[1])[1]
  }
  ]

  windows_instances = var.enable_windows ? [
  for entry in setproduct(var.x86_machine_types, var.x86_windows_boot_images) : {
    name    = "${entry[0]}-${split("/", entry[1])[1]}"
    type    = entry[0]
    image   = entry[1]
    os_name = split("/", entry[1])[1]
  }
  ] : []

  all_instances = concat(local.linux_instances, local.windows_instances)

  time = timestamp()
}

resource "google_compute_instance" "windows" {
  for_each = {
  for index, vm in local.windows_instances : vm.name => vm
  }

  name         = each.value.name
  machine_type = each.value.type
  tags         = ["https-server", "http-server"]

  metadata = {
    windows-startup-script-cmd = "googet -noconfirm=true update && googet -noconfirm=true install google-compute-engine-ssh"
    enable-windows-ssh         = "true"
    serial-port-logging-enable = "true"
    windows-startup-script-ps1 = templatefile(
      "${path.module}/startup.ps1",
      {
        bucket        = data.google_storage_bucket.results.name
        vm_name       = each.value.name
        bucket_folder = local.time
        vm_zone       = var.zone
        os_name       = each.value.os_name
        machine_type  = each.value.type
      })
  }

  boot_disk {
    initialize_params {
      image = each.value.image
    }
  }
  network_interface {
    network = "default"
    access_config {
      // Ephemeral public IP
    }
  }
  service_account {
    email  = google_service_account.default.email
    scopes = ["cloud-platform"]
  }

  depends_on = [
    google_project_service.compute,
    google_service_account.default,
    google_storage_bucket_iam_policy.storage_policy
  ]
}

resource "google_compute_instance" "linux" {
  for_each = {
  for index, vm in local.linux_instances : vm.name => vm
  }

  name                    = each.value.name
  machine_type            = each.value.type
  tags                    = ["https-server", "http-server"]
  metadata_startup_script = templatefile(
    "${path.module}/startup.sh",
    {
      bucket        = data.google_storage_bucket.results.name
      vm_name       = each.value.name
      bucket_folder = local.time
      vm_zone       = var.zone
      os_name       = each.value.os_name
      machine_type  = each.value.type
    })

  boot_disk {
    initialize_params {
      image = each.value.image
    }
  }
  network_interface {
    network = "default"
    access_config {
      // Ephemeral public IP
    }
  }
  service_account {
    email  = google_service_account.default.email
    scopes = ["cloud-platform"]
  }

  depends_on = [
    google_project_service.compute,
    google_service_account.default,
    google_storage_bucket_iam_policy.storage_policy
  ]
}

data "google_iam_policy" "compute_viewer" {
  binding {
    role    = "roles/compute.viewer"
    members = [
      "serviceAccount:${google_service_account.default.email}"
    ]
  }
}
resource "google_compute_instance_iam_policy" "compute_policy" {
  for_each = {
  for index, vm in local.all_instances : vm.name => vm
  }
  instance_name = each.value.name
  policy_data   = data.google_iam_policy.compute_viewer.policy_data
  depends_on    = [google_compute_instance.linux, google_compute_instance.windows]
}

data "google_storage_bucket" "results" {
  name = var.bucket
}
data "google_iam_policy" "storage_policy" {
  binding {
    role    = "roles/storage.admin"
    members = [
      "serviceAccount:${google_service_account.default.email}"
    ]
  }
}
resource "google_storage_bucket_iam_policy" "storage_policy" {
  bucket      = data.google_storage_bucket.results.name
  policy_data = data.google_iam_policy.storage_policy.policy_data
}
