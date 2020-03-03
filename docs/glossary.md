---
permalink: /glossary
---
# Java Dependency Glossary

- **Class path**: an ordered list of jar files, zip files, and directories, each of which
  contains Java class files.
  A [class loader](https://docs.oracle.com/javase/7/docs/api/java/lang/ClassLoader.html)
  typically transforms the fully package qualified name of a Java class
  into a file name and then searches for a class file with that name in a class path.
  
  When more than one entry in a class path contains a class file with the same name,
  the class loader returns the file in the first path entry.
  Other class files with the same name are unavailable.
  
  If a class loader fails to find any instance of a class, it asks its parent class loader
  to find the class. In a running VM there are usually multiple class loaders,
  each with its own class path, but for our purposes we can treat this as 
  a single class path formed by appending parent class paths to child class paths.

## Types of conflicts and compatibility

<a name="linkage-error"></a>
- **Linkage error**: an abnormal condition of a classpath in which a
  "class has some dependency on another class; however, the
  latter class has incompatibly changed after the compilation of the
  former class."<sup>[1](#myfootnote1)</sup> The reference can be
  through a class literal, a field access, or a method invocation.
  Linkage errors encountered at runtime manifest as a subclass of
  `LinkageError` such as `NoSuchMethodError`, `NoClassDefFoundError`,
  `NoSuchFieldError`, or similar errors.

  For example, the name, return type, modifiers, or arguments of a
  non-private method, field, or class in a dependency has changed in an
  incompatible way between the version of a class file supplied at
  compile time and the version available in the runtime class path. A
  public method may have been removed from a class or an extended class
  may have been made final.
  
  In cases where binary compatibility and source compatibility are the
  same, a linkage error is when compilation would fail if the libraries
  in the class path were all built together from their originating
  source code.
  
  - Opposite: **Linkage-compatible**.

<a name="behavior-conflict"></a>
- **Behavior conflict**: A class's implementation has changed in a way that
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
  means that all major Java build systems select the same version of each dependency
  in the module's dependency tree.
  - Major build systems currently include Maven and Gradle.
  - Direct dependencies comply with this rule in
    all major build systems, so the real concern is transitive dependencies.
  - Version alignment does not guarantee linkage compatibility.
  - Sub-type: **Upper version alignment**: Version alignment where the version
    that is selected is the highest version in the dependency tree.
    - Upper version alignment ensures that when packages are upgraded
      to higher versions which don't make any breaking changes, there
      will be no new linkage conflicts.


### Conflict relationships

- A particular conflict cannot be both a linkage error and behavior conflict.
  They are mutually exclusive.
- A combination of jars at runtime can have any number of linkage errors and
  behavior conflicts.

## States of compatibility

<a name="linkage-compatible"></a>
- **Linkage-compatible** (said of a particular version of A and a particular
  version of B): When these versions are used together, there are no linkage
  errors between A and B or their shared dependencies.
<a name="linkage-matchable-version"></a>
- **Linkage-matchable** (said of a particular version of A in relation
  to all versions of B): There exists some version of B such that the version of
  A and the version of B are linkage-compatible.

## Class reference graph

<a name="method-reference"></a>
- **Method reference**: a reference indicating that a _source class_ links to a method of
  a _target_ class.

<a name="field-reference"></a>
- **Field reference**: a reference indicating that a _source class_ accesses a field of
  a target class.

<a name="class-reference"></a>
- **Class reference**: a reference indicating that a _source class_ uses a _target
  class_ without referencing a specific field or method;
  for example, by inheriting from the class.

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

<a name="myfootnote1">1</a>: [Linkage Error (Java SE Platform 8)](https://docs.oracle.com/javase/8/docs/api/java/lang/LinkageError.html)

