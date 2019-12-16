# Release Steps

To release artifacts 'dependencies-parent', 'dependencies', and 'linkage-checker-enforcer-rules',
run `prepare_release.sh` with `dependencies` argument:

```
$ cd cloud-opensource-java
$ git checkout master
$ git pull
$ ./scripts/prepare_release.sh dependencies 1.0.0
```

Note: Google Cloud Libraries BOM release procedure [boms/cloud-oss-bom/RELEASING.md](
boms/cloud-oss-bom/RELEASING.md) is not part of this document.

Create a PR for the release, and get it approved.

Continue to Rapid workflow: [Cloud Tools for Java Development Practices: Releasing](
https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#run-the-rapid-workflow)

## Rapid builds

Run the following Rapid release pipelines:

| Artifact ID | Rapid project | Dependency |
| ---------- | ------------------ | --------- |
|dependencies-parent| [cloud-java-tools-cloud-opensource-java-parent-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-parent-kokoro-release)||
|dependencies| [cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release)|dependencies-parent|
|linkage-checker-enforcer-rules|[cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release)|dependencies-parent, dependencies|

The release pipelines can run concurrently. For example, you don't have to wait for
`dependencies-parent` pipeline before initiating `dependencies` pipeline. They use different
GCS buckets.

## Update the document

Update the version element of `linkage-checker-enforcer-rules` dependency in the usage section of the wiki:
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Enforcer-Rule


