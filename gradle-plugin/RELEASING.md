# Release Steps

To release the Linkage Checker Gradle plugin, run the following commands:

```
# At the root of cloud-opensource-java directory
$ ./scripts/prepare_release.sh gradle X.Y.Z # for example 0.1.0
...
Creating pull request for X.Y.Z-gradle into master in GoogleCloudPlatform/cloud-opensource-java

https://github.com/GoogleCloudPlatform/cloud-opensource-java/pull/1234
...
```

This outputs a PR for the release. Get it approved by somebody. Proceed to
the [Rapid workflow](
https://rapid.corp.google.com/#/project/cloud-java-tools-cloud-opensource-java-gradle-plugin-kokoro-release?showInactive=false)
with "vX.Y.Z-gradle" as committish.

## Confirm the Release

Once the Rapid workflow finishes successfully, check the
[Gradle plugin page for com.google.cloud.tools.linkagechecker](
https://plugins.gradle.org/plugin/com.google.cloud.tools.linkagechecker).
You should see the released version number in the page.

## Update the document

Update the plugin version listed in the document:

- https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-with-Gradle


