[JLBP-3] Use Semantic Versioning
--------------------------------

Definition of semantic versioning (SemVer): https://semver.org

- For the purpose of semantic versioning, the stable surface of a library
  serves as its "public API." Annotations can mark features
  (classes, methods, etc.) as unstable, and thus not part of the public API,
  such that semantic versioning rules don't have to apply. 
  - Examples of annotations:
    - Guava uses `@Beta`
    - grpc-java uses `@ExperimentalApi`
    - Google-cloud-java uses `@BetaApi`, `@InternalApi`, and
      `@InternalExtensionOnly`
  - Library documentation should point users to a tool (or tools) that can
    help them detect when they are using features marked with these
    annotations.
  - Even though semantic versioning rules don't apply to unstable features, it is
    recommended to bump a library's minor version if unstable features have
    surface breakages.
- Tools are available to help identify accidental incompatibilities within a
  major version. Examples:
  - [Java API Compliance Checker](https://lvc.github.io/japi-compliance-checker/)
  - [Clirr Maven Plugin](http://www.mojohaus.org/clirr-maven-plugin/)
  - [Java API Tracker: Compatibility report for grpc-core](
    https://abi-laboratory.pro/index.php?view=timeline&lang=java&l=grpc-core)
- Examples of breaking changes to a public API that require a new major
  version:
  - Upgrading to an incompatible dependency that is exposed through a
    library's public API. For dependencies that follow semantic versioning, this happens
    when a dependency is bumped to a higher major version.
  - Changing a method signature
  - Removing a method (deprecated or not)
- Examples of additions that require a new minor version:
  - Upgrading a dependency to a new minor version (compatible, but new
    features) that is exposed through a library's public API
  - Adding a new class
  - Adding a new method
- Special case: Merely bumping up the minimum required Java version (and not
  making any breaking surface changes) does not necessarily mean that a
  library should bump its major version, because new Java versions break
  very little surface from prior versions.
