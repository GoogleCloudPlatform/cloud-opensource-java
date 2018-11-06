Static Linkage Checker
======================

Static Linkage Checker is a tool that performs a [static linkage check](
../library-best-practices/glossary.md#static-linkage-check) on a classpath
and reports static linkage errors to the console.
It scans the class files in a classpath provided as input for references
to other classes. Each reference is verified to find linkage conflicts
in the classpath.
The tool also provides analysis on reachability to the errors from entry point
classes through class usage graphs.

### Use Cases

There are two use cases for Static Linkage Checker:

-  **For organizations** that provide multiple libraries developed by different teams,
  the tool helps to ensure that there are no static linkage conflicts among the libraries and their
  dependencies.

- **For library/application developers** the tool will assess the risk of static linkage
  conflicts in their projects, and will help to avoid incompatible versions of libraries
  in their dependencies.

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
with _reachability_. Each of the static linkage errors contains the information on the
source class and the destination class of the reference, and has one of the three types:

  - _Missing class type_ is for errors when where the destination class of a
    class reference does not exist in the classpath. This error
    happens when a class is removed in a different version of a library,
    or there is a dependency missed when constructing the classpath.
    The reference that causes a missing class error is called a _dangling reference_.

  - _Missing method type_ is for errors when a method reference has a static
    linkage conflict.

  - _Missing field type_ is for errors when a field reference has a static
     linkage conflict.
     
### Class Usage Graph and Reachability

In order to provide a diagnosis on the output report, the tool builds _class usage graphs_,
and annotates linkage errors with _reachability_ from _entry point classes_.
The tool allows users to choose the scope of entry point classes:

  - **Classes in the target project**: when the scope of the entry point is only the classes in the
    target project, it ensures that the current functionality used in the dependencies will not
    cause static linkage errors.
    The output may fail to report potential static linkage errors, which would be introduced
    by starting to use a previously unreachable class in one of the dependencies.

  - **Direct dependencies of the target project**: when the scope of the entry point is the all
    classes in the direct dependencies of the target project, it ensures that functionality of the
    dependencies will not cause static linkage errors. The output may contain linkage errors for
    unreachable classes from user's perspective.

