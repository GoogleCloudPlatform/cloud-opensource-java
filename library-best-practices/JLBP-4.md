[JLBP-4] Avoid dependencies known to cause runtime conflicts
------------------------------

- Avoid using dependencies that are not stable.
  - Example violation: The App Engine SDK introduced a non-final version of the
    JCache API into its classpath, which conflicts with the final version of
    JCache.
  - The current exception is JSR 305 (`@NonNull`). It is widely referenced in
    existing libraries and there is no compelling alternative, so it is allowed
    as a dependency. However, it should also be noted that jsr305 has classes in
    the javax.annotation package, which means that under the Java module system
    introduced in Java 9, it conflicts with other artifacts that use that
    package. The Error Prone team and others are in conversation with Jetbrains
    and others to produce a good replacement.
- Avoid using dependencies that duplicate classes with other dependencies.
  - Most common case: a library that has published the same classes under
    multiple artifact names. Always use the primary variant.
  - Example: Guava's main artifact is `guava`, but from versions 13.0 to 17.0,
    another artifact `guava-jdk5` was also published with classes that overlap
    with `guava`. Maven doesn't dedupe `guava-jdk5` with `guava` because the
    artifact names are different, which means that the same class names are
    provided by multiple jars, and runtime errors result from classes and
    methods not being found. The only known Google library propagating this
    dependency is google-api-client <= 1.23.0, and it has removed the
    `guava-jdk5` dependency (and switched to the `guava` artifact) since 1.24.1.
- Avoid depending on `servlet-api` if not necessary.
  - The only type of library that should depend on `servlet-api` is one
    functioning as a utility library for use in web apps. Even in this case, the
    `servlet-api` dependency should use `provided` scope. Other libraries should
    not use `servlet-api` as a dependency at all, for example, to get http
    status code definitions. Usage of this library needs to be restricted
    because there are multiple published versions that conflict with each other.
- New problematic dependencies may be added here later.
