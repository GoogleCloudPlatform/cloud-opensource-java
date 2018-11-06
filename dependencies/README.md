Static Linkage Checker
======================

Static Linkage Checker is a tool that inspects classpaths for [static linkage check](
../library-best-practices/glossary.md#static-linkage-check).
The tool identifies static linkage errors that appear in Java projects at runtime.
It scans the class files in a classpath provided as input for references
to other classes. Then it verifies compatibility of the methods and fields
in the referenced classes, with regard to their type signatures and accessors.

### Use Cases

There are three use cases for Static Linkage Checker:

-  **For organizations** that provide multiple libraries developed by different teams,
  the tool helps to ensure that there are no static linkage conflicts among the libraries and their
  dependencies when some of them are used at the same time.

- **For library developers**, the tool ensures that the users of the library will not suffer
  static linkage conflicts caused solely by the dependencies of the library.

- (future plan) **For application developers** consuming libraries, the tool will
  assess the risk of static linkage conflicts among the application's dependencies, and will help
  to avoid incompatible versions of libraries.

### Approach

1. Given an input, the tool prepares a _linkage classpath_, from which the tool inspects Java
  classes.
  Note that the linkage classpath is different from the runtime classpath of the tool itself.

2. The tool extracts all _references_ from the all class files in the classpath.

3. The tool checks the linkage compatibility of the references through the classpath, and records
  static linkage conflicts.
  
4. The tool traverses a _class usage graph_ to annotate the linkage errors with _reachability_.

5. At the end, the tool outputs a report on the linkage errors.

### Output

The tool reports zero or more static linkage errors for the linkage classpath, annotated
with _reachability_.

### Class Usage Graph and Reachability

In order to provide diagnosis on the output report, the tool builds _class usage graphs_,
and annotates linkage errors with _reachability_ from _entry point classes_.
The tool allows users to choose the scope of entry point classes:

  - **Classes in the target project**: when the scope of the entry point is only the classes in the
    target project, it ensures that the current functionality used in the dependencies will not
    cause static linkage errors.
    The output may fail to report potential static linkage errors, which would be introduced
    by starting to use a new class in one of the dependencies.

  - **Direct dependencies of the target project**: when the scope of the entry point is the all
    classes in direct dependencies of the target project, it ensures that functionality of the
    dependencies will not cause static linkage errors. The output may contain linkage errors for
    unused classes from user's perspective.

