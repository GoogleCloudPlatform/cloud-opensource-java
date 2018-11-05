Static Linkage Checker
======================

Static Linkage Checker identifies the
[linkage conflicts](../library-best-practices/glossary.md#types-of-conflicts-and-compatibility)
that would be caused by 3rd-party dependencies of Java projects at runtime. The tool scans
the class files in dependencies, and verifies availability of the implementation of the classes,
methods, and fields referenced by the files.

### Use Cases

There are three use cases for Static Linkage Checker:

-  **For organizations** that provide multiple libraries (through BOM) developed by different teams,
  the tool helps to ensure that there is no static linkage conflicts among the libraries and their
  dependencies.

- **For library developers**, the tool ensures that the users of the library will not suffer the
  static linkage conflicts caused solely by the dependencies of the library.

- (future plan) **For application developers** consuming 3rd-party libraries, the tool will
  assess the risk of static linkage conflicts among the application's dependencies, and will help
  to use appropriate versions of libraries to avoid runtime errors.

### Approach

1. Given an input, the tool prepares a _linkage classpath_, from which the tool inspects Java
  classes.
  

2. The tool extracts all _references_ from the class files in the classpath.

3. The tool checks the linkage compatibility of the references through the classpath.
  
4. To annotate _reachability_ on the linkage errors, the tool traverses a _class usage graph_.

5. Once the tool finishes traversal, then it outputs the report on linkage errors.


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
  graphs_ when running the analysis. The nodes (vertex) of the graph correspond to Java classes
  and the directed edges are references between classes. For example, when 'Class A' has reference
  to 'Class B' on calling a method X, the tool builds a graph holding an edge between two nodes:

    [Class A] --(method X of class B)-> [Class B]

  In this case, 'Class A' is called the _source class_ of the reference and 'Class B' is called
  the _destination class_.

- **A reference** between two classes is either _class reference_, _method reference_
  or _field reference_.

  A _method reference_ indicates the source class uses the (static or non-static) method of
  the destination class.

  A _field reference_ indicates the source class accesses the (static or non-static) field of
  the destination class.

  A _class reference_ indicates the source class uses the destination class without specific field 
  or method (e.g., inheritance).

  A reference is called _linkage conflicting_ when there is a linkage conflict
  (see [glossary.md](../library-best-practices/glossary.md)) caused by the reference.
  When the destination class does not exist in the linkage classpath, the reference is called
  _dangling reference_. When a reference does not have linkage error, then the reference is 
  called _linkage compatible_,

- **Reachability** is the attribute of static linkage errors to indicate whether a linkage
  error caused by a reference is reachable from the _entry point classes_ of the class usage
  graph; in other words, there exists a path of references in the graph between an entry point class
  and the reference causing linkage error. The path helps to diagnose why static linkage
  errors are introduced to the dependencies.

- **Entry Point Classes** are classes in the class usage graph and used to verify the reachability
  of static linkage errors. Users have choices on the scope of entry point classes:

  - **Classes in the target project**: when entry point are only the classes in the target project,
    it ensures the current functionality used in the dependencies will not cause static linkage 
    errors. The output may fail to report potential static linkage errors, which would be introduced
    by starting to use a new class in one of the dependencies.

  - **Direct dependencies of the target project**: when entry point are all classes in direct
    dependencies of the target project, it ensures that functionality of the dependencies will
    not cause static linkage errors. The output may contain linkage error for unused classes from
    user's perspective.
