# [JLBP-14] Specify a single, soft version of each dependency

Give the version of each dependency as a single value such as `2.3`
instead of a range such as `[2.3,2.9]`, `[2.3,)`, or even `[2.3]`.
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

Version ranges can be a security hole. A project that uses a range to select
the version of a dependency picks up new versions of that dependency when they're
pushed to Maven Central. Someone with release privileges can take advantage
of this to slip malicious code into projects without proper review. A variant
of this [attack in the node.js ecosystem was used to steal
Bitcoins](https://www.theregister.co.uk/2018/11/26/npm_repo_bitcoin_stealer/).

When a pom.xml specifies a "hard" requirement such as
`<version>[2.3]</version>` instead of a soft requirement such as
`<version>2.3</version>`, Maven insists on having exactly that version on the
classpath. If some other artifact anywhere in the dependency graph also
publishes a hard requirement or a version range that does not allow version
2.3, the build breaks. Furthermore, other artifacts that depend on your library
cannot upgrade from version 2.3, even to newer compatible versions that include
important bug and security fixes.

See https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification
for more information.
