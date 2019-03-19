To release artifacts 'dependencies-parent', 'dependencies', and 'linkage-checker-enforcer-rules':

1. Create a tag for a release

```
git checkout origin/master
git pull
scripts/prepare_release.sh X.Y.Z    # X.Y.Z is version number (without previs 'v')
```

This script creates a branch and tag with the version number.

2. Create a pull request for the branch in Github UI.

The pull request should be merged to master after confirming the build.

3. Initiate Rapid builds

| ArtifactId | Rapid project name |
| ---------- | ------------------ |
|dependencies| cloud-java-tools-cloud-opensource-java-dependencies-kokoro-release|
|dependencies-parent| cloud-java-tools-cloud-opensource-java-parent-kokoro-release|
|linkage-checker-enforcer-rules|cloud-java-tools-cloud-opensource-java-enforcer-rules-kokoro-release|


Specify the tag (vX.Y.Z) as 'committish'.

4. Confirm the builds

The artifacts should be available in Sonatype staging repository.

5. Merge version increment PR

Merge the PR created at Step 2.
