# Release Steps

## Prerequisites 

(Does not need to be repeated for each release.)

* Install the [`gh`](https://github.com/cli/cli)
tool if you not previously done so.

    * Run `gh auth login` to register your desktop with github.

* Clone this repository onto your corp desktop, Ubiquity instance, or CloudTop. Do not use a laptop or personal machine as the release requires google3 access.

## Release

To release artifacts 'dependencies-parent', 'dependencies', 'linkage-checker-enforcer-rules',
and 'gradle-plugin', run `script/release.sh` with `dependencies` argument:

```
$ cd cloud-opensource-java
$ git checkout master
$ git pull
$ ./scripts/release.sh dependencies 1.0.0
(This takes around 25 minutes)
```

Once the `release.sh` finishes and the pull request has been merged, update the "v1-linkagemonitor"
tag to this release:

```
$ git tag -d v1-linkagemonitor
$ git tag -a v1-linkagemonitor v1.0.0-dependencies -m "Linkage Monitor release on v1.5.5-dependencies"
$ git push -f origin v1-linkagemonitor
```

Note: Google Cloud Libraries BOM release procedure [boms/cloud-oss-bom/RELEASING.md](
boms/cloud-oss-bom/RELEASING.md) is not part of this document.

This script creates a PR for the release and initiates the Rapid workflows listed below.

## Rapid builds

Run the following Rapid release pipelines:

| Artifact ID | Rapid project | Dependency |
| ---------- | ------------------ | --------- |
|dependencies-parent| [cloud-java-tools-cloud-opensource-java-parent-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-parent-kokoro-release)||
|dependencies| [cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release)|dependencies-parent|
|linkage-checker-enforcer-rules|[cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release](http://rapid/cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release)|dependencies-parent, dependencies|
|linkage-checker-gradle-plugin|[cloud-java-tools-cloud-opensource-java-gradle-plugin-kokoro-release](https://rapid/cloud-java-tools-cloud-opensource-java-gradle-plugin-kokoro-release)|dependencies|
|linkage-monitor|[cloud-java-tools-cloud-opensource-java-linkage-monitor-kokoro-release](https://rapid/cloud-java-tools-cloud-opensource-java-linkage-monitor-kokoro-release)||

The release pipelines can run concurrently. For example, you don't have to wait for
`dependencies-parent` pipeline before initiating `dependencies` pipeline. They use different
GCS buckets.

## Update the document

Update the version of the enforcer rule and the Gradle plugin in the wiki pages:

- https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Enforcer-Rule
- https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-with-Gradle

