# [JLBP-5] Avoid dependencies that overlap classes with other dependencies

Definition: When the same fully qualified class name is provided by
  two or more distinct artifacts (different group IDs, different
  artifact IDs, or both) the classes are said to *overlap*.

This can happen in several ways:

* A library has published the same classes in
  artifacts with different group ID or artifact IDs.

* A third party forks a library under their own group ID but does not repackage the classes.

* A third party library copies an existing library's packages
into its own jar file without shading the dependency. This case is particularly
insidious since it may not be obvious that there's an unexpected, undocumented
version of the classes hiding inside the seemingly unrelated jar.

Example 1: Guava's main artifact ID is `guava`, but from versions 13.0 to 17.0,
  another artifact `guava-jdk5` was also published with classes that overlap
  with `guava`. Build systems such as Maven and Gradle cannot deduplicate
  `guava-jdk5` with `guava` because the artifact names are different.
  When both artifacts appear in the classpath, users encounter
  runtime errors resulting from classes and methods not being found.

Example 2: There are multiple artifacts that provide classes under
  `javax.servlet` (`javax.servlet:javax.servlet-api:3.1.0` and
  `javax.servlet:servlet-api:2.5` at least). The correct choice
  depends on the runtime.
  
  - The only type of library that should depend on `servlet-api` is
    one used exclusively in servlet applications. In this case,
    the `servlet-api` dependency should have `provided` scope. Other libraries should
    not depend on `servlet-api`. For example, do not use constants such as
    `javax.servlet.http.HttpServletResponse.SC_FORBIDDEN` if your library
    might be used anywhere that is not a servlet. Define these status codes
    yourself or choose a different library to provide them.

In Java 9 and later overlapping classes become compile-time and runtime errors when
named modules are used. It is critical, especially in Java 9 and later,
to remove all but one of the artifacts that contain overlapping classes from the classpath.
Generally this requires changing the POMs of multiple Maven artifacts so they no
longer include any dependencies on the artifacts you need to remove from your
project's classpath.

If this isn't possible, for instance because a dependency that imports an undesired
artifact is unmaintained, then add
[dependency exclusions](https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html)
for the artifacts you wish to remove in your own project's pom.xml.
