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


### Static Linkage Check

- **Static linkage check**: a process to identify the existence of static
  linkage errors for a given classpath, by scanning Java classes and
  verifying the availability in the classpath.

- **Static linkage report**: the outcome of a static linkage check. It reports
  zero or more static linkage errors.

- **Static linkage errors**: a reported error for a reference as the result of
  a static linkage check.
  A static linkage error contains the information on the source class and
  the destination class of the reference. Each static linkage error has
  one of the three types:

  - _Missing class type_ is the error when where the destination class of a
    class reference does not exist in the linkage classpath. This error
    happens when a class is removed in a different version of a library,
    or there is a dependency missed when constructing the linkage classpath.
    The reference that causes a missing class error is called a _dangling reference_.

  - _Missing method type_ is the error when a method reference has a  static
    linkage conflict.

  - _Missing field type_ is the error when a field reference has a static
     linkage conflict.

- **Linkage classpath**: the classpath which a static linkage check
  runs against. For a Maven project, a linkage classpath consists of the
  project's class files and the transitive dependencies induced by the direct
  dependencies in the project's pom.xml file.

- **Class usage graph**: a directed graph that represents the relationship between
  classes in the linkage classpath. The nodes (vertex) of the graph correspond to
  Java classes and the directed edges are references between classes.
  For example, when 'Class A' has reference to 'Class B' on calling a method X,
  the class usage graph holds an edge between two nodes:

  ```
  [Class A] --(method X of class B)-> [Class B]
  ```

  In this case, 'Class A' is called the _source class_ of the reference and
  'Class B' is called the _destination class_.

- **A reference**: in the class usage graph, the relationship between two 
  classes is either _class reference_, _method reference_ or _field reference_.

  A _method reference_ indicates that the source class uses the (static or
  non-static) method of the destination class.

  A _field reference_ indicates that the source class accesses the (static or
  non-static) field of the destination class.

  A _class reference_ indicates that the source class uses the destination
  class without referencing a specific field or method (e.g., class inheritance).

  A reference is called to have a _linkage conflict_
  when there is a static linkage conflict caused by the reference.

- **Reachability** is the attribute of static linkage errors to indicate
  whether a linkage error caused by a reference is _reachable_ from the _entry
  point classes_ of the class usage graph. In other words, when a static
  linkage error is _reachable_, there exists a path of references in the graph
  from one of the entry point classes to the reference causing linkage error.
  The path helps to diagnose why static linkage errors are introduced to the
  linkage classpath.

- **Entry point Classes** are classes in the class usage graph that are used
  to analyze the reachability of static linkage errors. These are the initial
  classes to start the graph traversal.

