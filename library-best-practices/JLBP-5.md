[JLBP-5] Avoid dependencies that overlap classes with other dependencies
------------------------------------------------------------------------

Definition: When two artifacts (with either a distinct group or distinct
  artifact id) "overlap classes," that means that there is at least one
  fully qualified class name provided by both of the artifacts.

This commonly happens when a library has published the same classes in
artifacts with different group ID or artifact IDs. Always use the primary variant.
This also happens when a third party forks a library under their own group
ID but does not repackage the classes.

It can also occur when a third library copies an existing library's packages
into its own jar file without shading the dependency. This case is particularly
insidous since it may not be obvious that there's an unexpected, undocumented
version of the classes hiding inside the seemingly unrelated jar. 

Example 1: Guava's main artifact is `guava`, but from versions 13.0 to 17.0,
  another artifact `guava-jdk5` was also published with classes that overlap
  with `guava`. Build systems such as Maven and Gradle can't dedupe `guava-jdk5`
  with `guava` because the artifact names are different, which means that the
  same class names are provided by multiple jars, and runtime errors result from
  classes and methods not being found. The only known Google library propagating
  this dependency is google-api-client <= 1.23.0, and it has removed the
  `guava-jdk5` dependency (and switched to the `guava` artifact) since 1.24.1.

Example 2: There are multiple artifacts that provide classes under
  `javax.servlet` (`javax.servlet:javax.servlet-api:3.1.0` and
  `javax.servlet:servlet-api:2.5` at least), and the correct one to choose
  depends on your runtime.
  - The only type of library that should depend on `servlet-api` is one
    functioning as a utility library for use in web apps. Even in this case, the
    `servlet-api` dependency should use `provided` scope. Other libraries should
    not use `servlet-api` as a dependency at all, for example, to get http
    status code definitions.
