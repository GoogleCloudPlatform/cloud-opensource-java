Java Dependency Glossary
------------------------

- **Class path**: an ordered list of jar files, zip files, or directories, each of which
  contains Java class files.
  A class loader searches a class file by its name through path entries in a class path.
  When there are two or more path entries (for example, jar files) that contains class files with
  the same name in a class path, the class file in the first path entry in the class path
  is available for use, and the other class files in the rest of the path entries are unavailable
  through the class path.

### Types of conflicts and compatibility

<a name="linkage-error"></a>
<a name="static-linkage-error"></a>
<a name="dynamic-linkage-error"></a>
- **Linkage error**: an error when a Java class references
  another class and the reference cannot be satisfied with the available classes in the class path.
  The reference can be through a class literal, a field access, or a method invocation.
  Linkage errors detected at runtime manifest as `ReflectiveOperationException`,
  `NoClassDefFoundError`, `NoSuchFieldException`, `MethodNotFoundException`,
  `LinkageError`, or other related exceptions.
  - Sub-type: **Linkage conflict**
  - Sub-type: **Missing class error**
  - Sub-type: **Static linkage error**: A linkage error caused by a direct code
    reference (non-reflective).
  - Sub-type: **Dynamic linkage error**: A linkage error caused by a reflective
    reference.

<a name="linkage-conflict"></a>
<a name="static-linkage-conflict"></a>
<a name="dynamic-linkage-conflict"></a>
- **Linkage conflict**: a linkage error when the signature, return type,
  modifiers, or throws declaration of a non-private method, field, or class
  in a dependency has changed (or removed) in an incompatible way between
  the version of a class file supplied at compile time and the version available in
  the runtime class path.
  For example, a public method may be removed from a class or an extended
  class may be made final.
  - Or, another perspective: In cases where binary compatibility and source
    compatibility are the same, a linkage conflict is when compilation would
    fail if the libraries in the class path were all built together from their
    originating source code, or when reflection would fail.
  - Opposite: **Linkage-compatible**.
  - Sub-type: **Static linkage conflict**: A linkage conflict caused by a direct
    code reference (non-reflective).
  - Sub-type: **Dynamic linkage conflict**: A linkage conflict caused by a
    reflective reference.

<a name="missing-class-error"></a>
- **Missing class error**: an error when a class referenced does not exist
  in the class path. This error happens when a class is removed in a different
  version of a library, or there is a dependency missed when constructing the class path.

<a name="static"></a>
- **Static**: Said of a linkage error when the linkage error is caused by a
  direct code reference (for example, _static linkage error_ and _static linkage conflict_).
  The references from a class are written in the class file when the class is compiled.

<a name="behavior-conflict"></a>
- **Behavior conflict**: The class's implementation has changed in a way that
  can break clients at runtime although all signatures remain compatible. For
  example, if a method that has been returning mutable lists begins returning
  immutable lists without updating its signature, dependents that mutate the
  list will fail, possibly far away from the original call site. By contrast, a
  change in return type from `ImmutableList` to `List` would be a linkage
  conflict if the calling code references anything in `ImmutableList` that is
  absent from `List` (for example, `ImmutableList.reverse()`).
  - Opposite: **Behavior-compatible**.

<a name="version-alignment"></a>
<a name="upper-version-alignment"></a>
- **Version alignment**: Said of the dependency tree of a Maven module. This
  means that for any dependency of a module in that module's dependency tree,
  all major Java build systems will select the same version of that dependency.
  - Major build systems currently include Maven and Gradle.
  - Direct (first-order) dependencies will trivially comply with this rule in
    all major build systems, so the real concern is transitive (second-order and
    higher) dependencies.
  - To achieve version alignment when multiple versions of a transitive
    dependency are present, a direct dependency on the transitive dependency
    needs to be added, in order to support Maven's dependency resolution logic
    (which selects the version with the fewest hops on the dependency tree).
  - Linkage compatibility is the desired result from version alignment, but not
    an inherent characteristic of the definition.
  - Sub-type: **Upper version alignment**: Version alignment where the version
    that is selected is the highest version in the dependency tree.
    - Using upper version alignment ensures that when packages are upgraded
      to higher versions which only add functionality and don't make any
      breaking changes, there will be no new linkage conflicts.


#### Conflict relationships

- A particular conflict cannot be both a linkage conflict and behavior conflict
  at the same time (they are mutually exclusive).
- A combination of jars at runtime can have any number of linkage conflicts and
  behavior conflicts.

### States of compatibility

<a name="linkage-compatible"></a>
- **Linkage-compatible** (said of a particular version of A and a particular
  version of B): When these versions are used together, there are no linkage
  conflicts between A and B.
<a name="linkage-matchable-version"></a>
- **Linkage-matchable version** (said of a particular version of A in relation
  to all versions of B): There exists some version of B such that the version of
  A and the version of B are linkage-compatible.

### Class reference graph

<a name="method-reference"></a>
- **Method reference**: a reference indicating that a _source class_ links to a method of
  a _target_ class.

<a name="field-reference"></a>
- **Field reference**: a reference indicating that a _source class_ accesses a field of
  a target class.

<a name="class-reference"></a>
- **Class reference**: a reference indicating that a _source class_ uses a _target
  class_ without referencing a specific field or method
  (for example, by inheriting from the class).

<a name="class-reference-graph"></a>
- **Class reference graph**: a possibly cyclic directed graph where each node represents
  a class and each edge represents a method, field or class reference from the
  source class to the target class.

  For example, when 'Class A' invokes method X on 'Class B',
  the class reference graph holds an edge between the two nodes:

  ```
  [Class A] --(method X of class B)-> [Class B]
  ```

  In this case, 'Class A' is called the _source class_ of the reference and
  'Class B' is called the _target class_.

  In general, there can be multiple edges between two nodes when
  a class references two or more members of another class.
  Self-loops, references from a class to a member of the same class, are possible and common.

<a name="reachability"></a>
- **Reachability** is the attribute of classes (nodes in the graph)
  and references (edges in the graph) to indicate whether they are
  _reachable_ from a class. For example, when a reference that causes
  a linkage error is marked as _reachable_ from 'Class A', it means that
  there exists a path of edges in the class reference graph from 'Class A'
  to the reference causing a linkage error.
  The path helps to diagnose how linkage errors are introduced to the
  class path from which the graph is built.

<a name="entry-point-class"></a>
- **Entry point class**: a class in the set of classes used to analyze
  the reachability of linkage errors. A graph traversal on the reachability
  of a linkage error starts with the nodes that correspond to the
  entry point classes.


