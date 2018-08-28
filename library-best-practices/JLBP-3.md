[JLBP-3] Use Semantic versioning
--------------------------------

Definition of semver: https://semver.org

- For the purpose of compliance with semver, the stable surface of a library
  serves as its "Public API." Annotations can be used to mark features
  (classes, methods, etc) as unstable, and thus not part of the Public API,
  such that semver rules don't have to apply. 
  - Examples of annotations:
    - Guava uses `@Beta`
    - grpc-java uses `@ExperimentalApi`
    - Google-cloud-java uses `@BetaApi`, `@InternalApi`, and
      `@InternalExtensionOnly`
  - Library documentation should let users know how to detect usage of
    features that use these annotations
  - Even though semver rules don't apply to unstable features, it is
    recommended to bump a library's minor version if unstable features have
    surface breakages.
- Tools are available to help identify accidental incompatibilities within a
  major version. Examples:
  - https://lvc.github.io/japi-compliance-checker/ 
  - http://www.mojohaus.org/clirr-maven-plugin/
  - Example compatibility report for grpc-core:
    https://abi-laboratory.pro/index.php?view=timeline&lang=java&l=grpc-core 
- Examples of breaking changes that require a new major version:
  - Upgrading to a non-compatible dependency that is exposed through a
    library's Public API (for dependencies that follow semver, this happens
    when a dependency is bumped to a higher major version)
  - Changing a method signature
  - Removing a deprecated method
- Examples of additions that require a new minor version:
  - Upgrading a dependency to a new minor version (compatible, but new
    features) that is exposed through a library's Public API
  - Adding a new class
  - Adding a new method
- Special case: Bumping up the minimum required Java version doesn't always
  require a major version bump. Requirements for skipping the major version
  bump:
  - Requirement 1: Only a pure change in the minimum required Java version
    is allowed. Any surface change that breaks users still requires a major
    version bump.
  - Requirement 2: The major version can be kept only when the obsoleted
    version has minimal usage. If an obsoleted version has medium or high
    usage, a major version bump may be warranted.
