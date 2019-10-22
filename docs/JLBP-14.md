# [JLBP-14] Do not use version ranges

Always specify a single version instead of a range for dependencies.
When a pom.xml specifies a range instead of a
single version for any particular dependency, builds at different points
in time can see different versions of that dependency (as your dependency
releases new versions), which can break your product unexpectedly.

Version ranges cause builds to be non-reproducible. Builds of the
same code with the same compiler at different
times can produce different artifacts with different behavior.
Version ranges can pull in incomplete releases that break the
build. For example, a [version range in appengine-gcs-client's
dependency on google-http-java-client broke multiple customers'
builds](https://github.com/GoogleCloudPlatform/appengine-gcs-client/issues/71)
when only half the artifacts in the project were pushed one afternoon.

Version ranges can be a security hole. A project that depends on a
library with a version range picks up new versions of that dependency as they're
pushed to Maven Central. This can be exploited by someone with release privileges
to slip malicious code into projects without proper review. A variant
of this [attack in the node.js ecosystem was used to steal
Bitcoins](https://www.theregister.co.uk/2018/11/26/npm_repo_bitcoin_stealer/).

See https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification
for more information.
