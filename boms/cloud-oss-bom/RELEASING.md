# Cloud Libraries BOM Release

Run `prepare_release.sh` with `bom` argument in `boms/cloud-oss-bom` directory:

```
$ cd cloud-opensource-java/boms/cloud-oss-bom
$ git checkout master
$ git pull
$ ../../scripts/prepare_release.sh bom <release version> [<post-release-version>]
```

Create a PR for the release, and get it approved.

## Rapid build

Rapid project is [cloud-java-tools-cloud-opensource-java-bom-kokoro-release](
http://rapid/cloud-java-tools-cloud-opensource-java-bom-kokoro-release).

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
