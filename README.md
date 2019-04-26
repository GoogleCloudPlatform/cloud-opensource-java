[![unstable](http://badges.github.io/stability-badges/dist/unstable.svg)](http://github.com/badges/stability-badges)

This project explores common infrastructure and best practices for open source
Java projects for the Google Cloud Platform.

# Google Cloud Platform Java Dependency Dashboard

[Google Cloud Platform Java Dependency Dashboard](
https://storage.googleapis.com/cloud-opensource-java-dashboard/dashboard/target/dashboard/dashboard.html)
(runs daily; work in progress) shows multiple checks on the consistency among
Google Cloud Java libraries. For manually generating the dashboard, see
[its README](./dashboard/README.md).

# Java Library Best Practices

[The Java Library Best Practices](./library-best-practices) are a set of rules
that we believe will minimize problems for consumers of interconnected Java
libraries.

# Linkage Checker Enforcer Rule

[Linkage Checker Enforcer Rule](./enforcer-rules)  is a Maven enforcer rule that
detects [linkage errors](
./library-best-practices/glossary.md#types-of-conflicts-and-compatibility) in
the current project.

# Google Library BOM

[Google Library BOM](boms/cloud-oss-bom) is a Bill-of-Materials (BOM) that
provides consistent versions of Google Cloud Java libraries that work together
without linkage errors.
To understand how BOMs help library users avoid dependency conflicts, read
[Declaring Dependencies on Google Java Libraries](DECLARING_DEPENDENCIES.md)

# Development

This project is built using _Maven_.

## Requirements

1. (Optional) The [Google Cloud SDK](https://cloud.google.com/sdk/); install
  this somewhere on your file system. This tool is used for uploading Google
  Cloud Platform Java Dependency Dashboard to Google Cloud Storage.

1. Maven 3.5.0 or later.

1. JDK 8

1. git

1. Clone the project to a local directory using `git clone
   git@github.com:GoogleCloudPlatform/cloud-opensource-java.git`.




