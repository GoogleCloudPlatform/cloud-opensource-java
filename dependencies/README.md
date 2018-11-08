# Static Linkage Checker

Static Linkage Checker is a tool that finds [static linkage errors](
../library-best-practices/glossary.md#types-of-conflicts-and-compatibility)
on a class path and reports the errors to the console.
It scans the class files in the class path for references to other classes and
reports any reference that cannot be satisfied in the class path.
It can report all such unsatisfied references or only those that are reachable from
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

1. The tool takes a class path as required input.
  This class path is called the _input class path_. The tool operates on this class path
  to find linkage errors and it is separated from the runtime class path of the tool itself.

2. The tool extracts all symbolic references from the all class files in the class path.

3. The tool records static linkage errors for symbolic references which cannot be satisfied
  in the class path.
  
4. Optionally, the user can specify a subset of elements in the class path as _entry points_.
  In that case, the tool will list only those references that are reachable
  from the classes in the entry points.

5. At the end, the tool outputs a report on the linkage errors.

### Input

The tool takes a class path through either a BOM as a Maven coordinates, 
a list of Maven coordinates, or a list of class and jar files in the filesystem.

When the input is a Maven BOM, the elements in the BOM are
converted to a list of Maven coordinates.
If the BOM imports another BOM, the elements of the second BOM are recursively
added to the list of Maven coordinates.

When the input is a list of Maven coordinates, they are resolved to a list of jar files
that consists of the artifacts and their dependencies.

The list of class and jar files forms the _input class path_.

### Output

The tool reports static linkage errors for the input class path, annotated
with _reachability_. Each of the static linkage errors contains the information on the
source class and the destination class of the reference, and has one of the three types:

  - _Missing class_: a class reference causes a static missing class error.

  - _Missing method_: a method reference has a static linkage conflict.

  - _Missing field_: a field reference has a static linkage conflict.
     
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

