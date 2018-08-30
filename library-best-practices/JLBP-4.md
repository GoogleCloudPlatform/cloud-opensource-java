[JLBP-4] Avoid dependencies on unstable libraries and features
--------------------------------------------------------------

- Unstable libraries are defined as libraries that allow for breaking changes to
  their Public API within the same major version. For libraries following
  semver, this means libraries with a 0.x.y version. (See [JLBP-3.md] for more
  details on the recommendations for following semver.)
- Unstable features are defined as features that are not part of the stable
  Public API of a stable library, often marked with annotations like `@Beta`.
  (See [JLBP-3.md] for more details on the recommendations for annotating
  unstable features.)
- If your library depends on an unstable library or feature, and that feature
  experiences a breaking change between versions, your library will be locked to
  a specific version of your dependency.
  - If you expose the unstable feature on your library's surface, then your
    library's current major version will be permanently locked to the version
    you initially exposed, and you won't be able to upgrade without making a
    breaking change to your users.
  - If you only use the unstable feature in your implementation, then each minor
    or patch version of your library will require a very specific version of
    your dependency, and it will be unsafe for your users to upgrade your
    library on its own, creating opportunities for hard-to-diagnose runtime
    conflicts for users.
- Given the consequences of depending unstable features in dependencies, avoid
  doing so.
  - Depending on unstable features between submodules of a single library is
    considered acceptable, given there is a way for users to ensure they are
    using compatible versions of the submodules together easily, e.g. by having
    the library use the same version for all submodules, or by providing a BOM.
- Additionally, avoid depending on libraries that implement non-finalized JSRs,
  even if the library otherwise meets the criteria for stability.
  - Example: App Engine depended on JSR-107 (JCache) before it was finalized,
    and now the version that App Engine requires conflicts with the final
    version.  This shouldn't be read as a warning to avoid App Engine, but
    instead an example to warn other library implementers when making new
    dependency choices.
  - The current exception is JSR 305 (`@NonNull`), specifically as implemented
    in `com.google.code.findbugs:jsr305`. This library is widely referenced in
    existing libraries and there is no compelling alternative, so it is allowed
    as a dependency. However, it should also be noted that `jsr305` has classes
    in the `javax.annotation` package, which means that under the Java module
    system introduced in Java 9, it conflicts with other artifacts that use that
    package. The Error Prone team and others are in conversation with Jetbrains
    and others to produce a good replacement.
