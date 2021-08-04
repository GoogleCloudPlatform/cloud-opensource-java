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
$ ./scripts/release.sh lts <release version> <post-release-version>
```

This script creates a pull request and initiates the Rapid release workflow.

Ask a teammate to review and approve the PR. 

## OSSRH

The Rapid workflow uploads the artifact to OSSRH staging repository.

[Instructions for releasing from OSSRH are on the internal team 
site](https://g3doc.corp.google.com/company/teams/cloud-java/tools/developers/releasing.md#verify-and-release).


## Making changes to a released LTS BOM

We maintain the branch of each release, such as `1.0.0-lts` and `2.0.0-lts`. Let's call
them _LTS release branches_. An LTS release branch has a name "N.0.0-lts", where N is
the major release version number.
They are configured as protected branches so that we do not accidentally make changes.

Any change for a patch release should be merged to the LTS release branches , rather than the master branch;
otherwise we cannot release multiple versions of the BOM at the same time.
Note that even when you prepare a patch release with higher patch number than 1,
you commit changes to `N.0.0-lts` branch.
For example, when you make changes for 2.0.5 LTS BOM release, your pull request would merge
the changes into `2.0.0-lts` branch. The branch includes all the changes between
2.0.0 and 2.0.4 releases.

The release script creates a release branch for a patch release by checking out the LTS release
branches (`N.0.0-lts`) if the releasing version has non-zero patch version.
For example, when you run the release script with argument `./scripts/release.sh lts 2.0.4`,
it creates a release branch `2.0.4-lts` from the LTS release branch `2.0.0-lts`,
(not from `2.0.3-lts`).


