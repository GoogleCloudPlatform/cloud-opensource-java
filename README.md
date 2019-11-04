[![unstable](http://badges.github.io/stability-badges/dist/unstable.svg)](http://github.com/badges/stability-badges)

This project explores common infrastructure and best practices for open source
Java projects for the Google Cloud Platform.

# Google Cloud Platform Java Dependency Dashboard

[Google Cloud Platform Java Dependency Dashboard](
https://storage.googleapis.com/cloud-opensource-java-dashboard/com.google.cloud/libraries-bom/snapshot/index.html)
(runs daily; work in progress) shows multiple checks on the consistency among
Google Cloud Java libraries. For manually generating the dashboard, see
[its README](./dashboard/README.md).

# Google Best Practices for Java Libraries

[Google Best Practices for Java Libraries](https://googlecloudplatform.github.io/cloud-opensource-java/)
are rules that minimize problems for consumers of interconnected Java libraries.

# Linkage Checker Enforcer Rule

[Linkage Checker Enforcer Rule](./enforcer-rules) is a Maven enforcer rule that
detects [linkage errors](
./library-best-practices/glossary.md#types-of-conflicts-and-compatibility) in
the current project.

# Google Libraries BOM

[Google Libraries BOM](https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/The-Google-Cloud-Platform-Libraries-BOM) is a Bill-of-Materials (BOM) that
provides consistent versions of Google Cloud Java libraries that work together
without linkage errors.

# Development

This project is built using _Maven_.

## Requirements

1. Maven 3.5.0 or later.

1. JDK 8

1. git

1. Clone the project to a local directory using `git clone
   git@github.com:GoogleCloudPlatform/cloud-opensource-java.git`.
