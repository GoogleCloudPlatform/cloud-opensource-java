# Linkage Monitor

Linkage Monitor prevents open source libraries in the Google Cloud Java orbit (GAX,
google-http-java-client, gRPC, etc) from releasing versions that introduce new [linkage errors](
../library-best-practices/glossary.md#types-of-conflicts-and-compatibility).

# Usage

Use this tool as part of presubmit checks in GitHub, it notifies when code or a dependency changes
in such a way as to introduce a new linkage error in [Google Cloud Libraries BOM](
../#google-libraries-bom);

Example presubmit build script:

```
set -e # fail if any of command fails
# Install artifacts to local Maven repository. The command depends on build system of the project.
mvn install -DskipTests

# Linkage Monitor
curl http://.../linkage-monitor-X.Y.Z-all-deps.jar # unless it's installed already
java -jar /path/to/linkage-monitor-X.Y.Z-all-deps.jar com.google.cloud:libraries-bom:1.1.2
```