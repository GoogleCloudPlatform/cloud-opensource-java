# Classpath Checker

Classpath Checker is a tool that finds [static linkage errors](
../library-best-practices/glossary.md#static-linkage-error)
on a class path and reports the errors to the console.
It scans the class files in the class path for references to other classes and
reports any reference that cannot be satisfied in the class path.
It can report all such unsatisfied references or only those that are reachable from
a given set of entry point classes.

### Use Cases
 
There are two use cases for Classpath Checker:

- **For library/application developers** the tool finds static linkage
  errors in their projects, and will help to avoid incompatible versions of libraries
  in their dependencies.

- **For organizations** that provide multiple libraries developed by different teams,
  the tool helps to ensure that users depending on the libraries will not see any
  static linkage errors at runtime.

### Approach

1. The tool takes a class path as required input.
  This class path is called the _input class path_ and is separate from
  the runtime class path of the tool itself. The tool operates on the input class path
  to find linkage errors.

2. The tool extracts all symbolic references from the all class files in the class path.

3. The tool records static linkage errors for symbolic references which cannot be satisfied
  in the class path.

4. Optionally, the user can specify a subset of elements in the class path as _entry points_.
  In that case, the tool will list only those references that are reachable
  from the classes in the entry points.

5. At the end, the tool outputs a report on the linkage errors.

### Input

The input of the tool is either the Maven coordinate of a BOM, 
a list of Maven coordinates, or a list of class and jar files in the filesystem.
All of these inputs are converted to a class path for the classpath check,
which is the _input class path_.

When the input is a Maven BOM, the elements in the BOM are
converted to a list of Maven coordinates.
If the BOM imports another BOM, the elements of the second BOM are recursively
added to the list of Maven coordinates. This list of Maven coordinates is handled
in the same way as a directly-provided list of coordinates (see below).

When the input is a list of Maven coordinates, they are resolved to a list of jar files
that consists of the artifacts and their dependencies. This list of jar files is
handled in the same way as a directly-provided list of jar files (see below).

When the input is a list of class and jar files, they are used directly as the _input class path_.

### Output

The tool reports static linkage errors for the input class path.
Each of the static linkage errors contains information on the
source class and the destination class of the reference, and has one of the three types:
_missing class_, _missing method_, or _missing field_.
     
### Class Reference Graph and Reachability

In order to provide a diagnosis in the output report, the tool builds a [class reference graph](
../library-best-practices/glossary.md#class-reference-graph),
and annotates linkage errors with [reachability](
../library-best-practices/glossary.md#reachability) from [entry point classes](
../library-best-practices/glossary.md#entry-point-class).
Optionally the tool outputs a report including only _reachable_ static linkage errors.
The tool allows users to choose the scope of entry point classes:

  - **Classes in the target project**: when the scope of the entry point is only the classes in the
    target project, it ensures that the current functionality used in the dependencies will not
    cause static linkage errors.
    The output may fail to report potential static linkage errors, which would be introduced
    by starting to use a previously unreachable class in one of the dependencies.

  - **With direct dependencies of the target project**: when the scope of the entry point is
    the classes in the target project and the all classes in the direct dependencies of the project,
    it ensures that functionality of the dependencies will not cause static linkage errors.
    The output may contain linkage errors for unreachable classes from user's perspective.

  - **All classes in the input class path**: when reachability check is off, then
    all static linkage errors from all classes in the classpath, regardless of the reachability,
    are reported.
