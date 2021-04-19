# Linkage Monitor

Linkage Monitor works as a presubmit check in a GitHub repository to prevent open source libraries
in the Google Cloud Java orbit (GAX, google-http-java-client, gRPC, etc.) from releasing versions
that introduce new [linkage errors](
https://jlbp.dev/glossary.html#types-of-conflicts-and-compatibility).

User documentation: [Linkage Monitor](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Monitor)

# Installation

This tool works as part of presubmit checks in the library projects in GitHub.
The check fails the build when code or a dependency changes in such a way as to introduce a new linkage error
in the [Google Cloud Libraries BOM](../README.md#google-libraries-bom).

There are two ways to set up Linkage Monitor check in a repository: GitHub Actions and shell
scripts.

## 1. GitHub Actions

If your project can build in GitHub Actions, add the following configuration to the
`.github/workflows/ci.yaml` in your repository:

```
name: ci
jobs:
  linkage-monitor:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [8, 11]
    steps:
    - uses: actions/checkout@v2
    - uses: actions/setup-java@v1
      with:
        java-version: ${{matrix.java}}
    - name: Install artifacts to local Maven repository
      run: >
        mvn install -B -V \
         -DskipTests=true \
         -Dclirr.skip=true \
         -Denforcer.skip=true \
         -Dmaven.javadoc.skip=true \
         -Dgcloud.download.skip=true
    - uses: GoogleCloudPlatform/cloud-opensource-java/linkage-monitor@v1-linkagemonitor
```

This Linkage Monitor job checks the compatibility of the locally-installed artifacts with the latest
Libraries BOM on Java 8 and Java 11 runtime.

The artifact installation step may differ by projects. For example, a Gradle project can use the
`publishToMavenLocal` task before calling the Linkage Monitor action:

```
    - name: Install artifacts to local Maven repository
      run: ./gradlew build publishToMavenLocal -x test -x signMavenJavaPublication
    - uses: GoogleCloudPlatform/cloud-opensource-java/linkage-monitor@v1-linkagemonitor
```

## 2. Shell Scripts

If your project cannot use GitHub Actions, you can use a simple shell script that downloads the
latest Linkage Monitor uber JAR file and executes it.

Example presubmit build script:

```
set -e # fail if any of command fails
# Install artifacts in the local Maven repository. The command depends on build system of the project.
mvn install -DskipTests

# For Gradle,
# ./gradlew build publishToMavenLocal -x test

# Get uber JAR unless it's installed already
curl https://storage.googleapis.com/cloud-opensource-java-linkage-monitor/linkage-monitor-latest-all-deps.jar 
java -jar linkage-monitor-latest-all-deps.jar com.google.cloud:libraries-bom
```

# Kokoro Jobs

## linkage-monitor-X.Y.Z-all-deps.jar
The Rapid project [cloud-java-tools-cloud-opensource-java-linkage-monitor-kokoro-release](
https://rapid/cloud-java-tools-cloud-opensource-java-linkage-monitor-kokoro-release) updates the
uber JAR file in Google Cloud Storage.

## linkage-monitor-latest-all-deps.jar
The Kokoro job that updates the latest uber jar (`linkage-monitor-latest-all-deps.jar`) is
`cloud-opensource-java/ubuntu/linkage-monitor-gcs.sh`.

The Kokoro config lives in google3 at 
`google3/devtools/kokoro/config/prod/cloud-opensource-java/ubuntu/linkage-monitor-gcs.cfg`

# Debugging Linkage Monitor

In case you need to get debug messages from Linkage Monitor, prepare the following file as
`logging.properties`:

```
.level = INFO
handlers= java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level = FINE
com.google.cloud.tools.dependencies.linkagemonitor.level = FINE
```

and pass a system property `-Djava.util.logging.config.file=logging.properties` to JVM.