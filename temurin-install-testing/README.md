# temurin-testing

This Terraform configuration installs Temurin on a matrix of GCE machine types and OS boot images.
The tests are split into three batches to prevent hitting standard compute quotas.

1. Create a file named `private.auto.tfvars` in this folder with contents:
   ```shell
   user_email = "[gcloud account email]"
   project_id = "[gcp project id]"
   bucket     = "[cloud storage bucket id for results]"
   ```

2. Invoke the tests
   * To invoke the full test suite, execute `./test-all.sh`
   * To invoke a single test batch, modify `inputs.auto.tfvars` to enable one batch, then execute
     `terraform apply`. To destroy these resources later, set the batch variable back
     to false and execute `terraform apply` again.
      * (Optional) Review the input matrix for the batch you've enabled in `variables.tf`.

3. (Parsing Results) Test results are uploaded to the specified GCS bucket at
   `gs://[BUCKET]/[TIMESTAMP]/[MACHINE TYPE]` and full logs are available
   at `gs://[BUCKET]/[TIMESTAMP]/logs`.

4. (Debugging) To SSH into a created VM instance:
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