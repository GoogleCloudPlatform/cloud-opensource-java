[JLBP-5] Avoid dependencies that overlap classes with other dependencies
------------------------------------------------------------------------

Definition: When the same fully qualified class name is provided by 
  two or more distinct artifacts (different group IDs, different 
  artifact IDs, or both) the classes are said to *overlap*.

This can happen in several ways:

* A library has published the same classes in
  artifacts with different group ID or artifact IDs.

* A third party forks a library under their own group ID but does not repackage the classes.

* A third library copies an existing library's packages
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
    
This can be extremely difficult to resolve. If at all possible, eliminate all but one of the 
overlapping dependencies. Otherwise pay very close attention to which version of 
each overlapping class is chosen. Make sure the project does not depend on any
behavior or API of the class that is not selected.
