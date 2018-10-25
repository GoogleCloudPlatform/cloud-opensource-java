[JLBP-14] Do not use version ranges
-----------------------------------

- If the pom.xml of your library specifies a version range instead of a
  single version for any particular dependency, builds at different points
  in time can get different versions of that dependency (as your dependency
  releases new versions), which could break your library unexpectedly.
  - Version ranges can even pull in incomplete releases that break your
    library's build (example:
    https://github.com/GoogleCloudPlatform/appengine-gcs-client/issues/71).
  - Thus, always specify a single version instead of a version range for
    dependencies.
- Single-element version ranges ("hard requirements") have a much different
  impact, and this rule does not apply to them.
  - See https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification
    for more information.
