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

