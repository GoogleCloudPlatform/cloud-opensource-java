# Linkage Monitor

Linkage Monitor works as a presubmit check in a GitHub repository to prevent open source libraries
in the Google Cloud Java orbit (GAX, google-http-java-client, gRPC, etc.) from releasing versions
that introduce new [linkage errors](
https://jlbp.dev/glossary.html#types-of-conflicts-and-compatibility).

User documentation: [Linkage Monitor](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Monitor)

# Installation

This tool works as part of presubmit checks in the library projects in GitHub. The check
fails the build when code or a dependency changes in such a way as to introduce a new linkage error in
the [Google Cloud Libraries BOM](../README.md#google-libraries-bom);

Example presubmit build script:

```
set -e # fail if any of command fails
# Install artifacts in the local Maven repository. The command depends on build system of the project.
mvn install -DskipTests

# For Gradle,
# ./gradlew build publishToMavenLocal -x test

# Get uber JAR unless it's installed already
curl https://storage.googleapis.com/cloud-opensource-java-linkage-monitor/linkage-monitor-latest-all-deps.jar 
java -jar linkage-monitor-X.Y.Z-all-deps.jar com.google.cloud:libraries-bom
```

# Kokoro Job to Update GCS Object

Kokoro job to update the GCS object is `cloud-opensource-java/ubuntu/linkage-monitor-gcs`.
