# Release Steps

To release artifacts 'dependencies-parent', 'dependencies', and 'linkage-checker-enforcer-rules',
follow the steps below.

Note: Cloud OSS BOM release procedure [boms/cloud-oss-bom/RELEASING.md](boms/cloud-oss-bom/RELEASING.md)
is not part of this pipeline.

## 1. Create a tag for a release

```
git checkout origin/master
git pull
scripts/prepare_release.sh X.Y.Z    # X.Y.Z is version number (without prefix 'v')
```

This script creates a branch and tag with the version number.


### Reverting prepare_release.sh

When you want to revert the effect of this step, delete the tag and the branch.

```
git tag -d v0.1.1
git push origin :v0.1.1
git branch -d 0.1.1  # branch does not have 'v' prefix
```

Delete branch in Github: https://github.com/GoogleCloudPlatform/cloud-opensource-java/branches

## 2. Create a pull request for the branch in Github UI.

The pull request should be merged to master after confirming the build.

## 3. Initiate Rapid builds

| ArtifactId | Rapid project name |
| ---------- | ------------------ |
|dependencies| cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release|
|dependencies-parent| cloud-java-tools-cloud-opensource-java-parent-kokoro-release|
|linkage-checker-enforcer-rules|cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release|

The order is not strict and they can run concurrently.

When asked in Rapid, specify the tag (vX.Y.Z) as 'committish'.

## 4. Confirm the builds

The artifacts should be available in Sonatype staging repository.

## 5. Merge version increment PR

Merge the PR created at Step 2.
