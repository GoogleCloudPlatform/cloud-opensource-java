# Cloud Libraries BOM Release

Run `prepare_release.sh` with `bom` argument in `boms/cloud-oss-bom` directory:

```
$ cd cloud-opensource-java/boms/cloud-oss-bom
$ git checkout master
$ git pull
$ ../../scripts/prepare_release.sh bom <release version> [<post-release-version>]
```

Create a PR for the release, and get it approved.

Continue to Rapid workflow: [Cloud Tools for Java Development Practices: Releasing](
https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#run-the-rapid-workflow)

## Rapid build

Rapid project is [cloud-java-tools-cloud-opensource-java-bom-kokoro-release](
http://rapid/cloud-java-tools-cloud-opensource-java-bom-kokoro-release).

## Update the docs

Several docs in this and other repositories need to be updated once the 
new release is available on Maven Central. Send pull requests that change the
version in these documents:

* https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/The-Google-Cloud-Platform-Libraries-BOM
* https://github.com/googleapis/google-http-java-client/blob/master/docs/setup.md
* https://github.com/googleapis/google-cloud-java/blob/master/TROUBLESHOOTING.md

## Deleting a release

Occasionally you may need to clean up after an aborted release, typically because the release script had
problems. If so:

1. Delete the release branch on Github.

2. Fetch the tags in your local client:

   ```
   $ git fetch --tags --force`
   ```
     
3. Delete the tag locally:

   ```
   $ git tag -d v2.6.0-bom
   Deleted tag 'v2.6.0-bom' (was 3b96602)
   ```

4. Push the deleted tag:
   
   ```
   $ git push origin :v2.6.0-bom
   To github.com:GoogleCloudPlatform/cloud-opensource-java.git
   - [deleted]         v2.6.0-bom
   ```
