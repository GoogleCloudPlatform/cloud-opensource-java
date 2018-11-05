Static Linkage Checker
======================

Static Linkage Checker identifies the [static linkage conflicts](
../library-best-practices/glossary.md#types-of-conflicts-and-compatibility)
that would be caused by dependencies of Java projects at runtime. Given a classpath provided from
user (e.g., a Maven project and its dependency), the tool scans the class files in the classpath
for references to other classes, and verifies the availability of the implementation through
the classpath.

### Use Cases

There are three use cases for Static Linkage Checker:

-  **For organizations** that provide multiple libraries developed by different teams,
  the tool helps to ensure that there is no static linkage conflicts among the libraries and their
  dependencies when some of them are used at the same time.

- **For library developers**, the tool ensures that the users of the library will not suffer
  static linkage conflicts caused solely by the dependencies of the library.

- (future plan) **For application developers** consuming libraries, the tool will
  assess the risk of static linkage conflicts among the application's dependencies, and will help
  to avoid incompatible versions of libraries.

### Approach

1. Given an input, the tool prepares a _linkage classpath_, from which the tool inspects Java
  classes.

2. The tool extracts all _references_ from the class files in the classpath.

3. The tool checks the linkage compatibility of the references through the classpath, and records
  static linkage conflicts.
  
4. The tool traverses a _class usage graph_ to annotate the linkage errors with _reachability_.

5. At the end, the tool outputs a report on the linkage errors.

### Output

- **Static Linkage Errors** are reported by the tool. A static linkage error is either
  a _dangling reference_ or a _static linkage conflict_.

  A _dangling reference_ is a reference where its destination class does not exist in the 
  linkage classpath. This error happens when a class is removed in a different version of a library
  , or there is a dependency missed when constructing the linkage classpath.
  
  A _static linkage conflict_ is described in [glossary.md](../library-best-practices/glossary.md).
  In short, this error happens when the destination class's implementation is not compatible from
  what it was available at the time of compilation of the source class, in a way that such 
  incompatibility causes `NoSuchFieldException`, `MethodNotFoundException`, or `LinkageError` when
  Java Virtual Machine tries to use the implementation.


### Class Usage Graph and Reachability

- **Linkage Classpath** is a list of jar files from which the tool inspects Java classes when
  running the tool against a project. A linkage classpath corresponds to the transitive dependencies
  induced by the direct dependencies in the target project's pom.xml file. This includes the
  dependencies marked as `provided` or `optional: true`.

- **Class Usage Graph**

  In order to provide better diagnosis on the output report, the tool builds _class usage
  graphs_. The nodes (vertex) of the graph correspond to Java classes and the directed edges are
  references between classes. For example, when 'Class A' has reference to 'Class B' on calling
  a method X, the tool builds a graph holding an edge between two nodes:

    [Class A] --(method X of class B)-> [Class B]

  In this case, 'Class A' is called the _source class_ of the reference and 'Class B' is called
  the _destination class_.

- **A reference** between two classes is either _class reference_, _method reference_
  or _field reference_.

  A _method reference_ indicates that the source class uses the (static or non-static) method of
  the destination class.

  A _field reference_ indicates that the source class accesses the (static or non-static) field of
  the destination class.

  A _class reference_ indicates that the source class uses the destination class without
  referencing a specific field or method (e.g., class inheritance).

  A reference is called to have a _linkage conflict_
  (see [glossary.md](../library-best-practices/glossary.md#types-of-conflicts-and-compatibility) )
  when there is a static linkage conflict that is caused by the reference between the source
  class and the destination class.
  When the destination class does not exist in the linkage classpath, the reference is called a
  _dangling reference_. When a reference does not have a linkage error, then the reference is 
  called _linkage compatible_,

- **Reachability** is the attribute of static linkage errors to indicate whether a linkage
  error caused by a reference is _reachable_ from the _entry point classes_ of the class usage
  graph. In other words, when a static linkage error is _reachable_, there exists a path of
  references in the graph from one of entry point classes to the reference causing linkage
  error.
  The path helps to diagnose why static linkage errors are introduced to the dependencies.

- **Entry Point Classes** are classes in the class usage graph that are used to verify the
  reachability of static linkage errors. Users of the tool have choices on the scope of entry
  point classes:

  - **Classes in the target project**: when the scope of the entry point is only the classes in the
    target project, it ensures that the current functionality used in the dependencies will not
    cause static linkage errors.
    The output may fail to report potential static linkage errors, which would be introduced
    by starting to use a new class in one of the dependencies.

  - **Direct dependencies of the target project**: when the scope of the entry point is the all
    classes in direct dependencies of the target project, it ensures that functionality of the
    dependencies will not cause static linkage errors. The output may contain linkage errors for
    unused classes from user's perspective.

