# LTS BOM Release

## Prerequisites 

For the prerequisites, see [Libraries BOM release's Prerequisites section](
../cloud-oss-bom/RELEASING.md).

## Steps

All on your corp desktop: 

1. Run gcert if you have not done so in the last twelve hours or so.

2. If this is a major version release, create a LTS release branch `N.0.x-lts`, where
N is the release number.

For example:

```
$ git checkout -b 2.0.x-lts origin/master
$ git push --set-upstream origin 2.0.x-lts
```

If this is a patch version release, the LTS release branch already exists.
No action required for this step #2.

3. Run `release.sh` with `lts` argument in 
the `cloud-opensource-java` directory:

```
$ ./scripts/release.sh lts <release version>
```

With the `lts` argument, this script creates a pull request and that bumps the patch version of
the LTS release branch (not the master branch).
Ask a teammate to review and approve the PR.

Behind the scenes, the release script creates a release branch based on the
corresponding LTS release branch.
For example, when you run the release script with argument `./scripts/release.sh lts 5.0.3`,
it creates a release branch `5.0.3-lts` based on the LTS release branch `5.0.x-lts`.

The script also initiates the Rapid release workflow.

4. After you finish the release, if this is a major version release, bump the major version of the
   BOM (`boms/cloud-lts-bom/pom.xml`) in the master branch.

## OSSRH

The Rapid workflow uploads the artifact to OSSRH staging repository.

[Instructions for releasing from OSSRH are on the internal team 
site](https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#verify-and-release).


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
