[JLBP-6] Avoid creating conflicts between major versions
------------------------------------------------------------------------

- A new major version should generally use a different Java package.
- There is a tradeoff: if an extremely large library (for example thousands of
  classes) breaks a single method, the cost of a package rename may be higher
  than dealing with the fallout of dealing with diamond dependency conflicts for
  that particular method. Only perform an in-place breakage if users can easily
  replace their usage of the old library with the new library. The larger the
  portion of a library that breaks between major versions, the higher the value
  is from renaming the java package.
- *If* the Java package is changed, use a different Maven ID (different group ID
  or artifact ID)
- *If* the Java package is kept the same, use the same Maven ID (same group ID
  and same artifact ID).
- In other words, rename the Java package if and only if the Maven ID changes.

Background
----------

When a library B in Java depends on another library A through the Maven
repository system, there are two handles that library B needs to use to access
library A:

1. The Maven coordinates, following the form
   `${groupId}:${artifactId}:${version}`, for example
   `com.google.guava:guava:26.0-jre`. The Maven coordinates are used to pull the
   library's jar from Maven Central. Given a group ID and artifact ID, Maven
   will choose only one version to pull in.
2. The Java package (the package used in `import` statements), for example
   `com.google.common.collect.ImmutableList`. The Java package is used to select
   a particular class within the jar pulled from Maven Central.

Now when breaking changes are introduced to library A between major version 1
and major version 2, a choice needs to be made: to rename or not rename? This
question applies to both items listed above, the Maven coordinates (1) and the
Java package (2). Consider the following renaming scenario:

- The Maven coordinates start as `g1:a1:1.0.0`
  - With no rename, the new major version's Maven coordinates are
    `g1:a1:2.0.0`. (Only one of the versions can be present in the dependency
    tree.)
  - With a rename, the new major version's Maven coordinates are
    `g2:a2:2.0.0`. (Both versions can be present in the dependency tree.)
- The Java package starts as `com.google.a1`
  - With no rename, the new major version's Java package is still
    `com.google.a1`
  - With a rename, the new major version's Java package is `com.google.a2`

Given this scenario, here are the possible combinations of renamings:

- **Don't rename Java package**:
  - **Don't rename Maven ID (case 1)**: This approach can result in diamond
    dependency conflicts because different branches of a dependency tree can
    depend on different major versions, and only one of them can be pulled in by
    Maven in a particular dependency tree. Users are forced to change their code
    between versions.
  - **Rename Maven ID (case 2)**: Users can easily pull in both the old jar
    (`g1:a1:1.0.0`) and the new jar (`g2:a2:2.0.0`) accidentally through
    transitive dependencies, because Maven treats the two artifacts as distinct,
    but since they have classes with the same Java packages, they are loaded in
    unpredictable ways, leading to runtime exceptions (for example
    `ClassNotFoundException`). It is difficult for users to ensure their build
    tree only includes one of the artifacts. **NEVER DO THIS**. Example: guava
    vs guava-jdk5.
- **Rename Java package**:
  - **Don't rename Maven ID (case 3)**: The classes from `g1:a1:1.0.0` and
    `g1:a1:2.0.0` could technically be used together, but since they share the
    Maven group ID and artifact ID, only one jar can be pulled in. Users are
    forced to change their code between versions. It is strictly better to also
    rename the Maven ID, so that the major versions can be used side by side.
    Alternatively, if the old Java package and new Java package are bundled
    together in the same Maven artifact, then both can be used side by side
    (same as case 4 below). The slight drawback is that the user's class space
    is polluted with both versions, whether both are used or not.
  - **Rename Maven ID (case 4)**: The two major versions can be used side by
    side, allowing users to incrementally transition from the old to the new
    version, or even use them side by side indefinitely if
    necessary. Transitioning fully to the new version does require code changes
    between versions, though. There will be no conflicts using this
    approach. This is the approach taken by Go, described in
    https://research.swtch.com/vgo-import .

Given the consequences, it seems clear that the two worst options are case 2
(renaming the Maven ID while keeping the Java package the same) and case 3
(renaming the Java package while keeping the Maven ID the same), and both should
be avoided.

Of the remaining two, case 4 (renaming both the Maven ID and the Java
package) is the least disruptive and the only option that won't lead to diamond
dependency conflicts, so it should be the generally preferred approach. However,
given that a full transition from one major version to the next requires
updating all import statements regardless of whether the corresponding classes
have changed, there is potentially a very high cost to this option, which should
be weighed carefully. Case 1 (neither renaming the Maven ID nor the
Java package) doesn't have this code updating cost, and the fewer the breakages,
the easier the transition is between major versions. Basically, the cost of
diamond dependency conflicts has to be weighed against the cost of updating
import statements everywhere the library is used.

Let's take examples from two extremes.

1. A library with 10,000 classes, and 1 function is deprecated between major
   version 1 to major version 2, and it is used in one place in a large
   dependency tree.

In this case, moving 10,000 classes to a new package in a large dependency tree
would be a very expensive endeavor. In contrast, updating the one place where
the old function is used to use the new function instead would be considerably
less work and could be rolled out much more quickly. In this scenario, it is
clearly superior to keep the same Maven ID and Java package.

3. A library with 1,000 classes, and a large refactoring touching 750 of the
   classes is done between major version 1 and major version 2.

In this case, changing consuming code would be a large undertaking, and it's not
certain that all consuming code would feel it's worth it to migrate to the new
major version. If the library author decided to opt for keeping the same Maven
ID and same Java package, the ecosystem would need to bifurcate in
order to handle code paths requiring one versus the other major version. Either
the ecosystem would generally opt to retain the old major version, or there
would be an extended period of difficult diamond dependency conflicts before
everyone had transitioned. In this scenario, it is clearly superior to rename
both the Maven ID and Java package.

Given these considerations, the rule to follow can be summed up as:

*When making breaking changes between major versions, prefer to rename both the
Maven ID and the Java package. However, if the ratio of surface
breakage to library size is sufficiently low and the number of usage points of
breaking code is sufficiently low, keeping the same Maven ID and Java
package can be preferred instead. Under no circumstance should only one of the
two be renamed without the other being renamed also.*

Example renames in open source 
[Square has established it as a policy](http://jakewharton.com/java-interoperability-policy-for-major-version-updates/)
for its Java libraries (examples include OkHttp and Retrofit). Numerous open
source packages have performed such a rename between large major version
updates:
- OkHttp (com.squareup.okhttp -> com.squareup.okhttp3)
- Apache Commons Lang (org.apache.commons.lang -> org.apache.commons.lang3)
- RxJava (rx (version 1.x) -> io.reactivex (version 2.x))
- JDOM (org.jdom -> org.jdom2)
- JUnit (junit.framework (versions 1.x-3.x) -> org.junit (version 4))
- jdeferred (org.jdeferred -> org.jdeferred2)

Numerous open source packages have chosen to retain package names between
major version updates:
- Guava
- Hibernate
- Joda Time
