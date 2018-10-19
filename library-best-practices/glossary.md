Java Dependency Glossary
------------------------

### Types of conflicts and compatibility

- **Linkage conflict**: The signature of a non-private method or class in a
  dependency has changed in an incompatible way between the version supplied at
  compile time and the version invoked at runtime. For example, a public method
  may be removed from a class or a class may be made final. Linkage conflicts
  detected at runtime manifest as `ReflectiveOperationException`,
  `NoClassDefFoundError`, `NoSuchFieldException`, `MethodNotFoundException`,
  `LinkageError`, or other related exceptions.
  - Or, another perspective: In cases where binary compatibility and source
    compatibility are the same, a linkage conflict is when compilation would
    fail if the libraries in the classpath were all built together from their
    originating source code, or when reflection would fail.
  - Opposite: **Linkage-compatible**.
  - Sub-type: **Static linkage conflict**: A linkage conflict caused by a direct
    code reference (non-reflective).
  - Sub-type: **Reflective linkage conflict**: A linkage conflict caused by a
    reflective reference.

- **Behavior conflict**: The class's implementation has changed in a way that
  can break clients at runtime although all signatures remain compatible. For
  example, if a method that has been returning mutable lists begins returning
  immutable lists without updating its signature, dependents that mutate the
  list will fail, possibly far away from the original call site. By contrast, a
  change in return type from List to ImmutableList would be a linkage conflict.
  - Opposite: **Behavior-compatible**.

- **Version disagreement**: This is a class of failure reported by build systems
  with strict version checking on. The main example is dependency convergence
  failures when using the Maven enforcer plugin, which checks that all branches
  of a dependency tree agree on the version of every dependency. A failure of
  dependency convergence doesn't guarantee a linkage or behavior conflict, and
  the passing of a dependency convergence check doesn't guarantee there will be
  no linkage or behavior conflicts. Consequently, version disagreement is a poor
  proxy for real problems (linkage and behavior conflicts).
  - Dependency convergence will fail even if the dependency tree only disagrees
    at the level of a patch version (1.10.0 vs 1.10.1) and there is no linkage
    or behavioral conflict.
  - It is possible for dependency convergence to pass when a version of a
    library is chosen at build time which is incompatible with another library
    in the tree. This is possible by forcing Maven to pick the "wrong" version
    using something like dependency management or dependency exclusion, which
    silences the Maven enforcer plugin.
  - Opposite: **Version agreement**.

- **Version alignment**: Said of the dependency tree of a Maven module. This
  means that for any dependency of a module in that module's dependency tree,
  all major Java build systems will select the same version of that dependency.
  - Major build systems currently include Maven and Gradle.
  - Direct (first-order) dependencies will trivially comply with this rule in
    all major build systems, so the real concern is transitive (second-order and
    higher) dependencies.
  - Generally, to achieve linkage compatibility (when assuming breaking changes
    aren't made within major versions), the upper bound of the versions in the
    tree should be selected.
    - This should fix all cases of linkage conflicts caused by purely additive
      functionality.
  - To achieve version alignment when multiple versions of a transitive
    dependency are present, a direct dependency on the transitive dependency
    will need to be added, in order to support Maven's dependency resolution
    logic (which selects the version with the fewest hops on the dependency
    tree).
  - Linkage compatibility is the desired result from version alignment, but not
    an inherent characteristic of the definition.

#### Conflict relationships

- A particular conflict cannot be both a linkage conflict and behavior conflict
  at the same time (they are mutually exclusive).
- A combination of jars at runtime can have any number of linkage conflicts and
  behavior conflicts.
- Version disagreement is a report by a tool, not an actual conflict. You can
  have version disagreement without either linkage or behavior
  conflicts (a false positive report), or either linkage or behavior conflicts
  without version disagreement (a false negative report).

### States of compatibility

- **Linkage-compatible** (said of a particular version of A and a particular
  version of B): When these versions are used together, there are no linkage
  conflicts between A and B.
- **Linkage-matchable version** (said of a particular version of A in relation
  to all versions of B): There exists some version of B such that the version of
  A and the version of B are linkage-compatible.
