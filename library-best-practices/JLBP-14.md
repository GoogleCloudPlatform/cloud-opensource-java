[JLBP-14] Use explicit versions, not version ranges
---------------------------------------------------

- If the pom.xml of your library specifies a version range instead of an
  explicit version, builds at different points in time can get different
  dependency versions that could break your library unexpectedly.
  - Version ranges can even pull in incomplete releases that will your
    library's build (example:
    https://github.com/GoogleCloudPlatform/appengine-gcs-client/issues/71).
  - Thus, always use an explicit version.
- Single-element version ranges ("hard requirements") have a much different
  impact, and this rule does not apply to them.
