[JLBP-6] Rename artifacts and packages together
-----------------------------------------------

When a library B in Java depends on another library A through the Maven
repository system, library B needs two identifiers to find classes in library A:

1. The Maven coordinates of library A, following the form
   `${groupId}:${artifactId}:${version}`; for example
   `com.google.guava:guava:26.0-jre`. The Maven coordinates are used to pull the
   library's jar from Maven Central. For each pair of group ID and artifact ID
   (hereafter referenced as "Maven ID"), the user's build system (for example
   Maven or Gradle) selects exactly one version to put on
   the classpath. Different build systems use different rules for selecting
   from multiple versions.
2. The fully-qualified class names of the classes in library A. These classes
   generally share a Java package (the package as defined in `package`
   statements, for example `package com.google.common.collect`). The classpath,
   which is formed from Maven artifacts and possibly non-Maven sources, is
   searched for each fully-qualified class name at runtime.

When breaking changes are introduced to library between major version 1 and
major version 2, a choice needs to be made: to rename or not rename? This
question applies to both items listed above, the Maven ID (1) and the Java
package (2).

Recommendations:

- When making a breaking change, take one of the following approaches:
  1. If the new library surface is delivered under a new Java package, either
     use a different Maven ID (different group ID or artifact ID) or bundle the
     old and new packages together under the original Maven ID.
  2. If the breaking change is made in-place and the Java package is kept the
     same, use the same Maven ID (same group ID and same artifact ID).

  There is a tradeoff between these two options. If a library breaks the
  surface for a method that is not used by any consumers, this does not cause
  any diamond dependency conflicts, so a package rename is
  a high-cost transition with no real benefit. Conversely, if a library breaks the
  surface for a method that is used by many clients, this causes numerous
  diamond dependency conflicts, so a package
  rename is preferable. Generally, the wider the usage of the features
  that need to break, the higher the value from renaming the Java package.
- Whether or not you make breaking changes, don't publish
  the same classes under multiple Maven IDs; this creates a situation where
  artifacts have "overlapping classes." Another best practice,
  [JLBP-5](JLBP-5.md), covers how consumers need to handle such problematic
  scenarios — don't create new cases!

  - Corollary 1: Once you have published under a particular Maven ID, you are
    stuck with it until you rename your Java package.
  - Corollary 2: Don't fork an artifact owned by someone else and publish
    classes with the same fully-qualified classnames.

Consider the following renaming scenario:

- The Maven coordinates start as `g1:a1:1.0.0`
  - With no rename of the Maven ID, the new major version's Maven coordinates
    are `g1:a1:2.0.0`. (Only one of the versions will be present on the
    classpath even if both are in the dependency tree.)
  - With a rename of the Maven ID, the new major version's Maven coordinates are
    `g2:a2:2.0.0`. (Both versions can be present on the classpath.)
- The Java package starts as `com.google.a1`
  - With no rename, the new major version's Java package is still
    `com.google.a1`.
  - With a rename, the new major version's Java package is `com.google.a2`.

Given this scenario, here are the possible combinations of renamings:

- **Don't rename Java package**:
  - **Case 1: Don't rename Maven ID**. This approach can result in diamond
    dependency conflicts because different branches of a dependency tree can
    depend on different major versions, and the build system (Maven or Gradle)
    will only choose one of them to use. Users are forced to change their code
    between versions, but only for library surface that was changed.
  - **Case 2: Rename Maven ID**. Users can easily pull in both the old jar
    (`g1:a1:1.0.0`) and the new jar (`g2:a2:2.0.0`) into their classpath
    accidentally through transitive dependencies because Maven artifact
    resolution treats the two artifacts as distinct.  This results in
    "overlapping classes" since they have classes with the same fully-qualified
    path. As a result, the classes are loaded in unpredictable ways, leading to
    runtime exceptions (for example `ClassNotFoundException`). It is difficult
    for users to ensure that their build tree only includes one of the
    artifacts. **NEVER DO THIS**.
- **Rename Java package**:
  - **Case 3: Don't rename Maven ID**. The classes from `g1:a1:1.0.0` and
    `g1:a1:2.0.0` could technically be used together, but since they share the
    Maven group ID and artifact ID, only one jar can be pulled in.
    Users must change their code to add references to the new version.
    It is strictly better to either also rename the Maven ID (case 4)
    or to keep the Maven ID and also bundle the old package (case 5), so that
    the major versions can be used side by side.
  - **Case 4: Rename Maven ID**. The two major versions can be used side by
    side, allowing users to incrementally transition from the old to the new
    version, or even use them side by side indefinitely if
    necessary. Transitioning fully to the new version in Java requires code
    changes between versions, even for classes whose surface remains the same,
    because all import statements need to be updated. There will be no diamond
    dependency conflicts using this approach, but adoption can be blocked if
    there are consuming libraries that have not also created new major versions
    that can accept new types from this library. This approach is essentially
    like creating a new library.
  - **Case 5: Bundle old and new in the existing Maven ID**. Like case 4, the
    two versions can be used side by side. The impact is the same as case 4,
    except with the slight drawback that the user's class space is polluted with
    both versions, whether both versions are used or not.
    The benefit of this approach is that users don't have to think about which
    Maven artifact to use and can just keep advancing the version.

Given the consequences, maintainers should avoid case 2
(renaming the Maven ID while keeping the Java package the same)
and case 3 (renaming the Java package while keeping the Maven ID the same).
Among the remaining three cases, the impact of the Maven ID change is minuscule compared
to the impact of a Java package rename, so the remaining discussion focuses
only on the Java package rename.

Basically, the cost of diamond dependency conflicts (due to not renaming) has to
be weighed against the cost of updating import statements everywhere the library
is used. Let's take examples from two extremes.

1. A library with 10,000 references throughout 100 packages, and which has a
   function with one reference in a leaf of the dependency graph that is deleted
   between major version 1 and major version 2.

   In this case, moving 10,000 references to a new package in a large dependency
   tree would be a very expensive endeavor. In contrast, updating the one place
   that references the deleted function to use the new function is
   considerably less work and can be rolled out much more quickly. In this
   scenario, it is clearly superior to keep the same Java package.

2. A library with 10,000 references throughout 100 packages, and a large
   refactoring breaks the surface of 5,000 of those references between major
   version 1 and major version 2.

   In this case, changing consuming code would be a large undertaking.
   It's likely that not all maintainers will feel it's worth migrating to the new
   major version. If the library author decides to keep the same Java
   package, the ecosystem has to bifurcate to handle code paths
   requiring one major version or the other.
   Some projects keep using the old version. Other projects will upgrade to the new version of
   the dependency.
   Therefore, there will be diamond dependency conflicts.
   In this scenario, it is clearly superior to rename the Java
   package, and essentially treat the new major version as a new library.

Note that both of these examples are for a library with a large number of places
that reference it. The fewer places that a library is referenced, and the closer
to the leaves of the graph that the library is referenced, the less impact there
is to the decision.

Examples in open source
-----------------------

**Case 1**
- Guava
- Hibernate
- Joda Time

**Case 2**
- `guava` vs `guava-jdk5`
  - This technically wasn't a new major version, but it is an example of case 2
    that has caused a lot of problems.
- `javax.servlet:javax.servlet-api:3.1.0` vs  `javax.servlet:servlet-api:2.5`

**Case 4**
- Square has [established this approach as a policy for its Java libraries](http://jakewharton.com/java-interoperability-policy-for-major-version-updates/)
  (examples include OkHttp and Retrofit).
  - OkHttp (com.squareup.okhttp -> com.squareup.okhttp3)
- Apache Commons Lang (org.apache.commons.lang -> org.apache.commons.lang3)
- RxJava (rx (version 1.x) -> io.reactivex (version 2.x))
- JDOM (org.jdom -> org.jdom2)
- jdeferred (org.jdeferred -> org.jdeferred2)

**Case 5**
- JUnit (junit.framework (versions 1.x-3.x) -> org.junit (version 4))
