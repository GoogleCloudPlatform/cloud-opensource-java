[JLBP-14] Use explicit versions, not version ranges
---------------------------------------------------

- Neither Maven nor Gradle have have a mechanism to locally lock versions like
  package managers in other languages. If the pom.xml of your library specifies
  a version range instead of an explicit version, it can pull in snapshot
  versions or incomplete releases that can break builds (example:
  https://github.com/GoogleCloudPlatform/appengine-gcs-client/issues/71). Thus,
  always use an explicit version.
- Single-element version ranges ("hard requirements") have a much different
  impact, and this rule does not apply to them.
