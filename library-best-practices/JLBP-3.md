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
  - Library documentation should point users to a tool (or tools) that can
    help them detect when they are using features marked with these
    annotations.
  - Even though semver rules don't apply to unstable features, it is
    recommended to bump a library's minor version if unstable features have
    surface breakages.
- Tools are available to help identify accidental incompatibilities within a
  major version. Examples:
  - https://lvc.github.io/japi-compliance-checker/ 
  - http://www.mojohaus.org/clirr-maven-plugin/
  - Example compatibility report for grpc-core:
    https://abi-laboratory.pro/index.php?view=timeline&lang=java&l=grpc-core 
- Examples of breaking changes to a Public API that require a new major
  version:
  - Upgrading to a non-compatible dependency that is exposed through a
    library's Public API (for dependencies that follow semver, this happens
    when a dependency is bumped to a higher major version)
  - Changing a method signature
  - Removing a method (deprecated or not)
- Examples of additions that require a new minor version:
  - Upgrading a dependency to a new minor version (compatible, but new
    features) that is exposed through a library's Public API
  - Adding a new class
  - Adding a new method
- Special case: Merely bumping up the minimum required Java version (and not
  making any breaking surface changes) shouldn't necessarily mean that a
  library should bump its major version, because new Java versions break
  very little surface from prior versions. When considering a bump in the
  minimum required Java version, prefer to wait until the abandoned version
  has minimal usage, so that the splash damage of breakage is minimized.
  If an obsoleted version has medium or high usage, a major version bump may
  be warranted; however, it makes little sense to do this if it is the only
  reason the major version is being bumped.
