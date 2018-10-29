Java Dependency Glossary
------------------------

### Types of conflicts and compatibility

- **Linkage conflict**: The signature, return type, modifiers, or throws
  declaration of a non-private method or class in a dependency has changed in an
  incompatible way between the version supplied at compile time and the version
  invoked at runtime. For example, a public method may be removed from a class
  or a class may be made final. Linkage conflicts detected at runtime manifest
  as `ReflectiveOperationException`, `NoClassDefFoundError`,
  `NoSuchFieldException`, `MethodNotFoundException`, `LinkageError`, or other
  related exceptions. 
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
  change in return type from `ImmutableList` to `List` would be a linkage
  conflict if the calling code references anything in `ImmutableList` that is
  absent from `List` (for example, `ImmutableList.reverse()`).
  - Opposite: **Behavior-compatible**.

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
