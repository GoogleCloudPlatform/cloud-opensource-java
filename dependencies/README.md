# Static Linkage Checker

Static Linkage Checker is a tool that finds [static linkage errors](
../library-best-practices/glossary.md#types-of-conflicts-and-compatibility)
on a classpath and reports the errors to the console.
It scans the class files in a classpath for references to other classes and
reports any reference that cannot be satisfied in the classpath.
It can report all such missing references or only those that are reachable from
a given set of entry point classes.

### Use Cases

There are two use cases for Static Linkage Checker:

- **For library/application developers** the tool finds static linkage
  errors in their projects, and will help to avoid incompatible versions of libraries
  in their dependencies.

-  **For organizations** that provide multiple libraries developed by different teams,
  the tool helps to ensure that users depending on the libraries will not see any
  static linkage errors at runtime.

### Approach

1. The tool takes a classpath as required input.

  The classpath is called as _linkage classpath_ on which the tool operates
  to find linkage errors and is separated from the runtime classpath of the tool itself.

2. The tool extracts all _references_ from the all class files in the classpath.

3. The tool records references that cannot be satisfied in the classpath as
  static linkage errors.
  
4. Optionally, the user can specify a subset of the classpath as _entry points_.
  In that case, the tool will list only those references that are reachable
  from the classes in the entry points.

5. At the end, the tool outputs a report on the linkage errors.

### Input

The tool takes a classpath through either a list of class and
jar files in filesystems, a list of Maven coordinates, or a Maven BOM.

A Maven BOM specified as a Maven coordinate is converted to a list of Maven coordinates.
A list of Maven coordinates is resolved to a list of jar files
that consists of the artifacts and their dependencies.
The list of jar files forms a classpath, on which the tool operate.

### Output

The tool reports static linkage errors for the input linkage classpath, annotated
with _reachability_. Each of the static linkage errors contains the information on the
source class and the destination class of the reference, and has one of the three types:

  - _Missing class type_: a class reference causes a static missing class error.

  - _Missing method type_: a method reference has a static linkage conflict.

  - _Missing field type_: a field reference has a static linkage conflict.
     
### Class Reference Graph and Reachability

In order to provide a diagnosis on the output report, the tool builds _class reference graphs_,
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

