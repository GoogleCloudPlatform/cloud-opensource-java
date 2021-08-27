# LTS BOM Release

## Prerequisites 

For the prerequisites, see [Libraries BOM release's Prerequisites section](
../cloud-oss-bom/RELEASING.md).

## Steps

All on your corp desktop: 

### 1. Decide the release version.

The release is either a patch release for an existing major version or a new major
version release.
Determine the version you're going to release by the following steps:

If this is a patch release for a major version release, then

- Checkout "_N_.0.x-lts" branch where _N_ is a major version you're releasing (for example
  [`1.0.x-lts`](https://github.com/GoogleCloudPlatform/cloud-opensource-java/tree/1.0.x-lts)).
- Read the `<version>` element of [boms/cloud-lts-bom/pom.xml](./pom.xml) in the branch.
  For example if it has version "1.0.5-SNAPSHOT", then next release version is "1.0.5".

If this is a major version release, then

- Checkout the master branch
- Read the `<version>` element of [boms/cloud-lts-bom/pom.xml](./pom.xml).
  For example, if the `boms/cloud-lts-bom/pom.xml` of the master branch has "3.0.0-SNAPSHOT", then
  next release verson is "3.0.0".

You can also check the previously released versions in [Maven Central: com.google.cloud:gcp-lts-bom](
https://repo1.maven.org/maven2/com/google/cloud/gcp-lts-bom/).

### 2. Run gcert

Run gcert if you have not done so in the last twelve hours or so.

### 3. If this is a major version release, create an LTS release branch 

(If this is a patch version release, the LTS release branch already exists.
No action required for this step.)

If this is a major version release, create an LTS release branch `N.0.x-lts`, where
N is the release number.

For example:

```
$ git checkout -b 2.0.x-lts origin/master
$ git push --set-upstream origin 2.0.x-lts
```

### 4. Run `release.sh` with `lts` argument

Checkout the **master** branch of this repository.

In the `cloud-opensource-java` directory, run `release.sh`:

```
$ ./scripts/release.sh lts <release version>
```

with the `lts` argument. For example: `./scripts/release.sh lts 1.0.5`

This script creates a pull request that bumps the patch version of
the LTS release branch (not the master branch).
Ask a teammate to review and approve the PR.

Behind the scenes, the release script creates a release branch based on the
corresponding LTS release branch.
For example, when you run the release script with argument `./scripts/release.sh lts 5.0.3`,
it creates a release branch `5.0.3-lts` based on the LTS release branch `5.0.x-lts`.

The script also initiates the Rapid release workflow, which usually finishes in 20
minutes.

### 5. Release the artifact via OSSRH staging repository

Once the Rapid workflow uploads the artifact to OSSRH staging repository, release the artifact
via its website.

[Instructions for releasing from OSSRH are on the internal team 
site](https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#verify-and-release).

### 6. If this is a major version release, bump the major version in master branch

After you finish the release, if this is a major version release, bump the major version of the
BOM (`boms/cloud-lts-bom/pom.xml`) in the master branch.

### 7. Confirm the artifact in Maven Central

In 30 minutes, the new version appears in  [Maven Central: com.google.cloud:gcp-lts-bom](
https://repo1.maven.org/maven2/com/google/cloud/gcp-lts-bom/).

## Making changes for a patch release

Changes for a patch release should be committed to LTS release branches (not to the master branch).

We maintain multiple branches for the LTS BOM releases.
Let's call the branches _LTS release branches_.
An LTS release branch has a name "N.0.x-lts", where N is the major release version number,
such as `1.0.x-lts` and `2.0.x-lts`.
In this GitHub repository, the branches are [configured as protected](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/settings/branches),
so that we do not accidentally push changes into them.

Any changes for a patch release to the LTS BOM should be merged to one of the LTS release branches.
This practice enables us to release a patch version of one of the old versions of the BOM,
without using the master branch (or _HEAD_).

## Deleting a release

In case you have to clean up the releases, such as when you need to retry the release upon a
failure, please refer to [Libraries BOM release's Deleting a release section](
../cloud-oss-bom/RELEASING.md).