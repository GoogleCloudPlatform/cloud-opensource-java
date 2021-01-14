# Cloud Libraries BOM Release


## Prerequisites 

(Does not need to be repeated for each release.)

* Install the [`gh`](https://github.com/cli/cli)
tool if you not previously done so.

    * Run `gh auth login` to register your desktop with github.

* Clone this repository onto your corp desktop, Ubiquity instance, or CloudTop. Do not use a laptop or personal machine as the release requires google3 access.

## Steps

All on your corp desktop: 

1. Run gcert if you have not done so in the last twelve hours or so.

2. Run `release.sh` with `bom` argument in 
the `cloud-opensource-java` directory:

```
$ ./scripts/release.sh bom <release version> <post-release-version>
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

### Build the release binary with Rapid (Legacy web UI)

The [instructions for the Rapid build are on the internal team 
site](https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#run-the-rapid-workflow).

## OSSRH

[Instructions for releasing from OSSRH are on the internal team 
site](https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#verify-and-release).

## Update the docs

Several docs in this and other repositories need to be updated once the 
new release is available on Maven Central.

* Send pull requests that change the version in these documents:
    * https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/The-Google-Cloud-Platform-Libraries-BOM
      (no PR required)
    * https://github.com/googleapis/google-http-java-client/blob/master/docs/setup.md
    * https://github.com/googleapis/google-cloud-java/blob/master/TROUBLESHOOTING.md
* Ask a code owner for java-docs-samples to merge the dependabot PR
  that updates libraries-bom in https://github.com/GoogleCloudPlatform/java-docs-samples/pulls
* Manually edit and update any pom.xml files in https://github.com/GoogleCloudPlatform/java-docs-samples that dependabot missed
* In google3 run:
    * `$ g4d -f bom`
    * `/google/src/head/depot/google3/devtools/scripts/replace_string "&lt;version>oldVersion&lt;/version>" "&lt;version>newVersion&lt;/version>"`
    * `/google/src/head/depot/google3/devtools/scripts/replace_string "&lt;version&gt;oldVersion&lt;/version&gt;" "&lt;version>newVersion&lt;/version>"`
    * Sanity check the cl and send it for review.
    * Submit on approval
* Search for libraries-bom in google3 to find any internal references (typically cloudite and devsite) that still need to be updated.

## Retrying a failed release

If the Github steps succeed--PR created, version tagged, etc.--but the Rapid release fails, you can
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
