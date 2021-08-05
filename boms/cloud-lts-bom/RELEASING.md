# LTS BOM Release

## Prerequisites 

For the prerequisites, see [Libraries BOM release's Prerequisites section](
../cloud-oss-bom/RELEASING.md).

## Steps

All on your corp desktop: 

1. Run gcert if you have not done so in the last twelve hours or so.

2. Run `release.sh` with `lts` argument in 
the `cloud-opensource-java` directory:

```
$ ./scripts/release.sh lts <release version>
```

This script creates a pull request and initiates the Rapid release workflow.

Ask a teammate to review and approve the PR. 

## OSSRH

The Rapid workflow uploads the artifact to OSSRH staging repository.

[Instructions for releasing from OSSRH are on the internal team 
site](https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#verify-and-release).


## Making changes for a patch release

Changes for a patch release should be committed to LTS release branches (not the master branch).

We maintain multiple branches for the LTS BOM releases.
Let's call the branches _LTS release branches_.
An LTS release branch has a name "N.0.0-lts", where N is the major release version number,
such as `1.0.0-lts` and `2.0.0-lts`.
In this GitHub repository, the branches are configured as protected,
so that we do not accidentally push changes to them.

Any change for a patch release to the LTS BOM should be merged to one of the LTS release branches.
This practice enables us to release multiple versions of the BOM at the same time.
Note that even when you prepare a patch release with higher patch number than 1,
you commit changes to `N.0.0-lts` branch (the patch part is `0`).
For example, when you make changes for LTS BOM release 5.0.3, your pull request would merge
the changes into `5.0.0-lts` branch.
The branch includes all the changes between 5.0.0 and 5.0.2 releases.

Example pull request: https://github.com/GoogleCloudPlatform/cloud-opensource-java/pull/2165

This pull request was created for LTS BOM patch release 1.0.1.
Notice that the destination branch is `1.0.0-lts`.

## Performing a patch release

The command for patch releases is the same as the normal releases: 

```
$ ./scripts/release.sh lts <patch release version>
```

Behind the scene, for a patch release the release script creates a release branch based on the
corresponding LTS release branch.
The script determines whether it's a patch release or not by checking the version
has non-zero patch part.
For example, when you run the release script with argument `./scripts/release.sh lts 5.0.3`,
it creates a release branch `5.0.3-lts` based on the LTS release branch `5.0.0-lts`.

