Java Dependency Glossary
------------------------

### Types of conflicts and compatibility

- **Linkage error**: an error when a Java class in a classpath references
  another class (as a class literal, a field access, or a method invocation),
  and the reference cannot be satisfied with the available classes in the classpath.
  Linkage errors detected at runtime manifest as `ReflectiveOperationException`,
  `NoClassDefFoundError`, `NoSuchFieldException`, `MethodNotFoundException`,
  `LinkageError`, or other related exceptions.
  - Sub-type: **Linkage conflict**
  - Sub-type: **Missing class error**

  - **Linkage conflict**: a linkage error when the signature, return type,
    modifiers, or throws declaration of a non-private method, field, or class
    in a dependency has changed (or removed) in an incompatible way between
    the version supplied at compile time and the version invoked at runtime.
    For example, a public method may be removed from a class or a class may be made final.
    - Or, another perspective: In cases where binary compatibility and source
      compatibility are the same, a linkage conflict is when compilation would
      fail if the libraries in the classpath were all built together from their
      originating source code, or when reflection would fail.
    - Opposite: **Linkage-compatible**.
    - Sub-type: **Static linkage conflict**: A linkage conflict caused by a direct
      code reference (non-reflective).
    - Sub-type: **Dynamic linkage conflict**: A linkage conflict caused by a
      reflective reference.

  - **Missing class error**: an error when a class referenced does not exist
    in the classpath. This error happens when a class is removed in a different
    version of a library, or there is a dependency missed when constructing the classpath.

  - **Static**: Said of a linkage error when the linkage error is caused by a
    direct code reference (e.g., _static linkage error_ and _static linkage conflict_).
    The references from a class is written in the class file when the class is compiled.

- **Behavior conflict**: The class's implementation has changed in a way that
  can break clients at runtime although all signatures remain compatible. For
  example, if a method that has been returning mutable lists begins returning
  immutable lists without updating its signature, dependents that mutate the
  list will fail, possibly far away from the original call site. By contrast, a
  change in return type from `ImmutableList` to `List` would be a linkage
  conflict if the calling code references anything in `ImmutableList` that is
  absent from `List` (for example, `ImmutableList.reverse()`).
  - Opposite: **Behavior-compatible**.

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

- **Linkage-compatible** (said of a particular version of A and a particular
  version of B): When these versions are used together, there are no linkage
  conflicts between A and B.
- **Linkage-matchable version** (said of a particular version of A in relation
  to all versions of B): There exists some version of B such that the version of
  A and the version of B are linkage-compatible.


### Class usage graph

- **Class usage graph**: a possibly cyclic directed graph of references
  between classes. The nodes (vertices) of the graph correspond to
  Java classes and the directed edges are references between classes.
  For example, when 'Class A' invokes method X on 'Class B',
  the class usage graph holds an edge between the two nodes:

  ```
  [Class A] --(method X of class B)-> [Class B]
  ```

  In this case, 'Class A' is called the _source class_ of the reference and
  'Class B' is called the _destination class_.

  In general, there can be multiple (parallel) edges between two nodes when
  a class references two or more methods and fields of another class.
  Self-loops (references between the same class) are possible and
  common.

- **Reference**: A relationship from one class to another class such that
  the first class requires the presence of the second class in a particular
  form in order to function. A reference is represented as an edge a class usage graph.
  A reference is either a _class reference_, _method reference_ or _field reference_:

  A _class reference_ indicates that the source class uses the destination
  class without referencing a specific field or method (e.g., class inheritance).

  A _method reference_ indicates that the source class invokes a method of the
  destination class.

  A _field reference_ indicates that the source class accesses a field of the
  destination class.


- **Reachability** is the attribute of classes (nodes in the graph)
  and references (edges in the graph) to indicate whether they are
  _reachable_ from a class. For example, when a reference that causes
  a linkage error is marked as _reachable_ from 'Class A', it means that
  there exists a path of edges in the class usage graph from 'Class A'
  to the reference causing a linkage error.
  The path helps to diagnose how linkage errors are introduced to the
  classpath from which the graph is built.

- **Entry point classes** are a set of classes in the classpath to analyze
  the reachability to a linkage errors. A graph traversal on the reachability
  of a linkage error starts with the nodes that correspond to the
  entry point classes.
  

