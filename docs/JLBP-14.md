# [JLBP-14] Do not use version ranges

If the pom.xml of your library specifies a version range instead of a
single version for any particular dependency, builds at different points
in time can see different versions of that dependency (as your dependency
releases new versions), which can break your library unexpectedly.

  - Version ranges cause builds to be non-reproducible. Builds of the
    same code with the same compiler on different machines at different
    times can produce different artifacts with arbitrarily different behavior.

  - Version ranges can pull in incomplete releases that break your
    library's build (example:
    https://github.com/GoogleCloudPlatform/appengine-gcs-client/issues/71).

  - Version ranges can be a security hole. When a new version of the dependency
    is pushed to Maven Central, it will be picked up by downstream dependencies
    automatically. This can be used a by a malicious actor with release privileges
    to slip new malicious code into projects without proper review. [A version
    of this attack has been used in the node.js ecosystem to steal
    Bitcoins](https://www.theregister.co.uk/2018/11/26/npm_repo_bitcoin_stealer/).

  Thus, always specify a single version instead of a version range for dependencies.

Single-element version ranges ("hard requirements") have a much different
impact, and this rule does not apply to them.

[The Maven POM Reference](https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification)
contains more information.
