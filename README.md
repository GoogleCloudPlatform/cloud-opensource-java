[![unstable](http://badges.github.io/stability-badges/dist/unstable.svg)](http://github.com/badges/stability-badges)

This project explores common infrastructure, toolset and practices 
to provide consistency among Google Cloud Java libraries. The consistency
minimizes the risk of dependency conflicts caused by the libraries.

# Subprojects

## Google Cloud Platform Java Dependency Dashboard

[Google Cloud Platform Java Dependency Dashboard](
https://storage.googleapis.com/cloud-opensource-java-dashboard/dashboard/target/dashboard/dashboard.html)
(runs daily; work in progress) shows multiple checks on the consistency among
Google Cloud Java libraries. For manually generating the dashboard, see
[its README](./dashboard/README.md).

## Java Library Best Practices

[The Java Library Best Practices](./library-best-practices) are a set of rules
that we believe will minimize problems for consumers of interconnected Java
libraries.

## Linkage Checker Enforcer Rule

[Linkage Checker Enforcer Rule](./enforcer-rules) verifies that the transitive
dependency tree of a Maven project or a [BOM](
https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Management)
does not have any [linkage errors](
./library-best-practices/glossary.md#types-of-conflicts-and-compatibility).

## Cloud OSS BOM

[Cloud OSS BOM](boms/cloud-oss-bom) is a Bill-of-Materials (BOM) that holds
list of Google Cloud Java libraries with their versions.
We choose the managed dependencies so that they are consistent with each other
as much as possible.
For understanding how BOMs help you avoid dependency conflicts, read
[Declaring Dependencies on Google Java Libraries](DECLARING_DEPENDENCIES.md)

# Development

This project is built using _Maven_.

## Requirements

1. The [Google Cloud SDK](https://cloud.google.com/sdk/); install
  this somewhere on your file system. This is used for uploading Google Cloud
  Platform Java Dependency Dashboard to Google Cloud Storage.

1. Maven 3.5.0 or later.

1. JDK 8

1. git

1. Clone the project to a local directory using `git clone
   git@github.com:GoogleCloudPlatform/cloud-opensource-java.git`.




