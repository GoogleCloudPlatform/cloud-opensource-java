[![unstable](http://badges.github.io/stability-badges/dist/unstable.svg)](http://github.com/badges/stability-badges)

This project explores common infrastructure and best practices for open source
Java projects for the Google Cloud Platform (GCP).

# Google Best Practices for Java Libraries

[Google Best Practices for Java Libraries](https://googlecloudplatform.github.io/cloud-opensource-java/)
are rules that minimize problems for consumers of interconnected Java libraries.

# Linkage Checker

## Linkage Checker Maven Enforcer Rule

[Linkage Checker Enforcer Rule](./enforcer-rules) detects [linkage errors](
./library-best-practices/glossary.md#types-of-conflicts-and-compatibility) in
the current Maven project as part of build.

For its usage, see [the enforcer rule documentation](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Enforcer-Rule).

## Linkage Checker Gradle Plugin

[Linkage Checker Gradle Plugin](./gradle-plugin) provides the linkageCheck task that detects
[linkage errors](
./library-best-practices/glossary.md#types-of-conflicts-and-compatibility) in the current
Gradle project.

For its usage, see [the plugin documentation](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-with-Gradle).

# GCP Libraries BOM

The [GCP Libraries BOM](https://cloud.google.com/java/docs/bom) is a Bill-of-Materials (BOM) that
provides consistent versions of Google Cloud Java libraries that work together
without linkage errors.

This has moved to https://github.com/googleapis/java-cloud-bom/tree/main/libraries-bom.

# Development

This project is built using _Maven_.

## Requirements

1. Maven 3.6.0 or later.

1. JDK 8 or 11.

1. git

1. Clone the project to a local directory using `git clone
   git@github.com:GoogleCloudPlatform/cloud-opensource-java.git`.

# Disclaimer

This is not an officially supported Google product.

