[JLBP-5] Avoid dependencies that overlap classes with other dependencies
------------------------------------------------------------------------

Definition: When the same fully qualified class name is provided by 
  two or more distinct artifacts (different group IDs, different 
  artifact IDs, or both) the classes are said to *overlap*.

This can happen in several ways:

* A library has published the same classes in
  artifacts with different group ID or artifact IDs.

* A third party forks a library under their own group ID but does not repackage the classes.

* A third party library copies an existing library's packages
into its own jar file without shading the dependency. This case is particularly
insidous since it may not be obvious that there's an unexpected, undocumented
version of the classes hiding inside the seemingly unrelated jar.

Example 1: Guava's main artifact ID is `guava`, but from versions 13.0 to 17.0,
  another artifact `guava-jdk5` was also published with classes that overlap
  with `guava`. Build systems such as Maven and Gradle cannot deduplicate
  `guava-jdk5` with `guava` because the artifact names are different.
  When Guava classes overlapped in the two jars, users suffered from 
  runtime errors resulting from classes and methods not being found.
  The only Google Cloud library propagating
  this dependency was google-api-client <= 1.23.0, and it has removed the
  `guava-jdk5` dependency (and switched to the `guava` artifact) since 1.24.1.

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
    
Problems caused by overlapping classes can be extremely difficult to resolve.
If at all possible, eliminate all but one of the
overlapping dependencies. Otherwise pay very close attention to which version of 
each overlapping class is chosen. Make sure the project does not depend on any
behavior or API of the class that is not selected.
