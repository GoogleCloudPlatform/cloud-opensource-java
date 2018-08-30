[JLBP-4] Avoid dependencies on unstable libraries and features
--------------------------------------------------------------

- At a high level, don't depend on things that can have frequent breaking
  changes (where frequent means more than once every couple years).
- The primary type of unstable library is one that allows for breaking changes
  to their Public API within the same major version. For libraries following
  semver, this means libraries with a 0.x.y version. (See [JLBP-3](JLBP-3.md)
  for more details on the recommendations for following semver.)
- Another type of unstable library is one that provides stable versions
  according to semver, but which bumps major versions frequently.
  - The primary example of this is Guava before version 22.0, which published a
    new major version once or more per year.
- Unstable features are features that are not part of the stable Public API of a
  stable library, often marked with annotations like `@Beta`.  (See
  [JLBP-3](JLBP-3.md) for more details on the recommendations for annotating
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
    acceptable, provided that users can easily force their build system to use
    compatible versions of the submodules. Some strategies for library owners
    include:
    - Using the same version number for all submodules in a library
    - Providing a [BOM](https://www.baeldung.com/spring-maven-bom)
- Additionally, avoid depending on libraries that implement non-finalized JSRs,
  even if the library otherwise meets the criteria for stability (for example,
  using a stable version like 1.0.0).
  - A non-finalized JSR can change before reaching the Final status, meaning
    that an implementation of the JSR would need to make a breaking change.
  - Example: App Engine depended on JSR-107 (JCache) before it was finalized,
    and now the version that App Engine requires conflicts with the final
    version.  This shouldn't be read as a warning to avoid App Engine, but
    instead an example to warn other library implementers when making new
    dependency choices.
  - The current exception is JSR 305 (`@NonNull`), whose JSR status is currently
    "Dormant." This JSR is specifically implemented in
    `com.google.code.findbugs:jsr305`. This library is widely referenced in
    existing libraries, it is not experiencing breaking changes, and there is no
    compelling alternative, so it is allowed as a dependency. However, it should
    also be noted that `jsr305` has classes in the `javax.annotation` package,
    which means that under the Java module system introduced in Java 9, it
    conflicts with other artifacts that use that package. The Error Prone team
    and others are in conversation with Jetbrains and others to produce a good
    replacement.
