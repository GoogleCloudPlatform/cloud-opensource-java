# Cloud Libraries BOM Release


## Prerequisites 

(You do not need to repeat this section for each release.)

Install the following tools into a corp desktop where you have `p4` and `blaze` commands.

* Install [Maven](https://maven.apache.org/install.html) version 3.6 or later.

* Install the [`gh`](https://github.com/cli/cli/releases) tool. The automation creates a pull
  request via this command. From its release page, choose a binary that ends with
  "_linux_amd64.tar.gz". Place the `gh` executable somewhere in the `PATH` environment variable.

    * Run `gh auth login` to register your desktop with github.

* Clone this repository onto your corp desktop, Ubiquity instance, or CloudTop. Do not use a laptop or personal machine as the release requires google3 access.

* Install and configure the [repo tool](https://github.com/googleapis/github-repo-automation).
  This updates code samples across ~100 repositories. You need `config.yaml` when you run it:
  ```
  config.yaml
  ---
  githubToken: <fill your github token to manage pull requests>
  repoSearch: org:googleapis language:java is:public archived:false
  ```

## Decide the release version

To decide the release version, check the changes since [the last release](
https://search.maven.org/artifact/com.google.cloud/libraries-bom).
(This step ensures that you do not miss expected libraries upgrades.)
For example, if the last BOM release was version 16.4.0, then run the following command
to see the difference.

```
git diff v16.4.0-bom -- boms/cloud-oss-bom/pom.xml
```

If the difference includes the google-cloud-bom version (`google.cloud.bom.version`),
then check the change in the release note
at https://github.com/googleapis/java-cloud-bom/releases as well. Here is how to read
the release note:

- If there's a note that only mentions a major version bump, for example
  "*to v2*", then it's a major version bump.
- If there's a note that mentions a minor version bump, for example "*to v2.1.0*",
  then it's a minor version bump.

From these changes in the content of the Libraries BOM,
determine the release version by the following logic:

- If there is at least one major version bump among the changes, it's a major version bump.
- If there is at least one minor version bump (no major version change), it's a minor version
  bump.
- If there are only patch version bumps (no major or minor version change), it's a patch version
  bump.

We use the release version for `release.sh` in the next steps.

## Steps

All on your corp desktop: 

1. Run gcert if you have not done so in the last twelve hours or so.

2. Run `release.sh` with `bom` argument in 
the `cloud-opensource-java` directory:

```
$ sh ./scripts/release.sh bom <release version>
```

You might see this message:

```
Notice: authentication required
Press Enter to open github.com in your browser...
```

Do it. This grants the script permission to create a PR for you on Github.

Ask a teammate to review and approve the PR. 

If you want the script to stop asking your username and password for every invocation,
run `git config credential.helper store`.

## OSSRH

[Instructions for releasing from OSSRH are on the internal team 
site](https://g3doc.corp.google.com/company/teams/cloud-java/internal/g3doc/tools/releasing.md#verify-and-release).

## Update the docs

Several docs in this and other repositories need to be updated once the 
new release is available on Maven Central.

* Send pull requests that change the version in these documents:
    * https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/The-Google-Cloud-Platform-Libraries-BOM
      (no PR required)
    * https://github.com/googleapis/google-http-java-client/blob/master/docs/setup.md
    * https://github.com/googleapis/google-cloud-java/blob/master/TROUBLESHOOTING.md
* In google3 run:
    * `$ scripts/update_docs.sh <old version> <new version>`
      * For example, `$ scripts/update_docs.sh 16.3.0 16.4.0`
      * When asked whether to add changes to the first CL, answer "y".
    * Sanity check the CL and send it for review. Use "Suggest reviewers" to pick a reviewer.
    * Submit on approval

### Document updates on the next day

Few hours after the artifact becomes available in Maven Central, RenovateBot creates pull requests
to update the Libraries BOM version in the code samples.

* Ask a code owner for java-docs-samples to merge the dependabot PR
  that updates libraries-bom in https://github.com/GoogleCloudPlatform/java-docs-samples/pulls
* Use the repo tool to approve Renovate Bot updates for libraries-bom in
  the individual clients:
    * `$ repo --title '.*libraries-bom to v16.4.0' list`
      (for a major version bump, the pattern would be abbreviated. For example, use pattern `'.*libraries-bom to v17'` for 17.0.0 release)
    * Verify that the listed PRs look correct and don't include anything you're not ready to merge.
    * `$ repo --title '.*libraries-bom to v16.4.0' approve`
    * `$ repo --title '.*libraries-bom to v16.4.0' tag automerge`

  In case when the "Owlbot" check fails, use 'owlbot:run' label to rerun the checks.

## Retrying a failed release

If the Github steps succeed—PR created, version tagged, etc.—but the Rapid release fails, you can
run this command from a g4 client to retry the Rapid build without going all the way
back to the beginning:

```
$ blaze run java/com/google/cloud/java/tools:ReleaseRapidProject -- \
    --project_name=cloud-java-tools-cloud-opensource-java-bom-kokoro-release \
    --committish=v${VERSION}-bom
```

## Deleting a release

Occasionally you need to clean up after an aborted release, typically because the release script had
problems. If so:

1. Delete the release branch on Github.

2. Run `scripts/cancel_release.sh <version>`

3. If the release got as far as uploading a binary to Nexus before you cancelled, then
login to OSSRH and drop the release.


The `cancel_release.sh` script performs these steps:


1. Fetch the tags in your local client:

   ```
   $ git fetch --tags --force
   ```
     
2. Delete the tag locally:

   ```
   $ git tag -d v2.6.0-bom
   Deleted tag 'v2.6.0-bom' (was 3b96602)
   ```

2. Push the deleted tag:
   
   ```
   $ git push origin :v2.6.0-bom
   To github.com:GoogleCloudPlatform/cloud-opensource-java.git
   - [deleted]         v2.6.0-bom
   ```
