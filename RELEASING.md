# Release Steps

To release artifacts 'dependencies-parent', 'dependencies', and 'linkage-checker-enforcer-rules',
follow the "Developers/Releasing" steps in go/ct4j.

Note: Cloud OSS BOM release procedure [boms/cloud-oss-bom/RELEASING.md](
boms/cloud-oss-bom/RELEASING.md) is not part of this document.

## Rapid builds

| ArtifactId | Rapid project name | Dependency |
| ---------- | ------------------ | --------- |
|dependencies-parent| cloud-java-tools-cloud-opensource-java-parent-kokoro-release||
|dependencies| cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release|dependencies-parent|
|linkage-checker-enforcer-rules|cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release|dependencies-parent, dependencies|

To satisfy their dependency tree, their dependency need to be released together.

The release pipelines can run concurrently. For example, you don't have to wait for
`dependencies-parent` pipeline before initiating `dependencies` pipeline.

## Merge PR to increment version at the last step

After finishing the upload to Maven Central, merge the PR created for the branch created by
"scripts/prepare_release.sh". This increments the version in master.
