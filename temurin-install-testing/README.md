# temurin-install-testing

This Terraform configuration installs Temurin on a matrix of GCE machine types and OS boot images.
The tests are split into three batches to prevent hitting standard compute quotas.

1. (Optional) Create a file named `private.auto.tfvars` in this folder with contents:
   ```shell
   user_email = "[gcloud account email]"
   project_id = "[gcp project id]"
   bucket     = "[cloud storage bucket id for results]"
   ```

2. Modify `inputs.auto.tfvars` to enable one test batch.
    * (Optional) Review the input matrix for the batch you've enabled in `variables.tf`.

3. ```shell
   terraform apply
   ```

4. When the results have been uploaded to the specified GCS bucket, disable the current test batch
   in `inputs.auto.tfvars`, execute `terraform apply` to destroy the current VMs, then repeat from
   step 2 with a new batch.

5. (Parsing Results) Test results are uploaded to the specified GCS bucket at
   `gs://[BUCKET]/[TIMESTAMP]/[MACHINE TYPE]` and full logs are available
   at `gs://[BUCKET]/[TIMESTAMP]/logs`.

6. (Debugging) To SSH into a created VM instance:
    * Find the VM instance name by either navigating to
      https://console.cloud.google.com/compute/instances, or by looking at Terraform's console
      output.
        * Example Terraform output:
          ```
          google_compute_instance.default["n2-standard-2-centos-stream-8"]: Creation complete after 14s [id=projects/PROJECT_ID/zones/ZONE_ID/instances/n2-standard-2-centos-stream-8]
          ```
          Where `n2-standard-2-centos-stream-8` is the VM instance name.
    * Invoke the `connect.sh` helper script with the VM instance name:
      ```shell
      ./connect.sh [VM_INSTANCE_NAME]
      ```
      Example:
      ```shell
      ./connect.sh n2-standard-2-centos-stream-8
      ```

## Support

This repository is not intended for public consumption.

For source code and support for Google cloud libraries, start here:

https://cloud.google.com/apis/docs/cloud-client-libraries