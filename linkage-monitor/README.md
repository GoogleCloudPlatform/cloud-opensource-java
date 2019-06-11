# Linkage Monitor

The goal of Linkage Monitor is to prevent open source libraries in the Google Cloud Java orbit (GAX,
google-http-java-client, gRPC, etc.) from releasing versions that introduce new [linkage errors](
../library-best-practices/glossary.md#types-of-conflicts-and-compatibility).

# Usage

This tool will work as part of presubmit checks in the library projects in GitHub. This check will
notify when code or a dependency changes in such a way as to introduce a new linkage error in
[Google Cloud Libraries BOM](../README.md#google-libraries-bom);

Example presubmit build script:

```
set -e # fail if any of command fails
# Install artifacts to local Maven repository. The command depends on build system of the project.
mvn install -DskipTests

curl http://.../linkage-monitor-X.Y.Z-all-deps.jar # unless it's installed already
# Latest version of Google Cloud Libraries BOM. Example: "1.1.1"
LATEST_VERSION=`curl 'https://search.maven.org/solrsearch/select?q=g:%22com.google.cloud%22+AND+a:%22libraries-bom%22&core=gav&rows=1&wt=json' |perl -nle 'print $1 while m/"v":"(.+?)"/g'`
java -jar /path/to/linkage-monitor-X.Y.Z-all-deps.jar com.google.cloud:libraries-bom:${LATEST_VERSION}
```