# Linkage Checker

Linkage Checker is a tool that finds [linkage errors](
../library-best-practices/glossary.md#static-linkage-error) on a class
path. It scans the class files in the class path for references to other
classes and reports any reference that cannot be satisfied.

### User Documentation

Linkage Checker can be used from Maven or Gradle.

- For Maven, [Linkage Checker Enforcer Rule](
  https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Enforcer-Rule)
- For Gradle, [Gradle Linkage Checker plugin](
  https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-with-Gradle)

### Use Cases
 
There are two use cases for Linkage Checker:

- **For library/application developers** the tool finds linkage
  errors in their projects and helps to avoid incompatible versions of libraries
  in their dependencies.

- **For organizations** that provide multiple libraries developed by different teams,
  the tool helps to ensure that users depending on these libraries will not see any
  linkage errors at runtime.

### Approach

1. The tool takes a class path as input. This is called the _input class
   path_ and is separate from the runtime class path of the tool itself.

2. The tool extracts symbolic references from the class files in the class path.

3. The tool records linkage errors for symbolic references that cannot be satisfied
   in the class path.

4. At the end, the tool outputs a report of the linkage errors.

### Input

The input to the tool is the Maven coordinate of a BOM, 
a list of Maven coordinates, or a list of class and jar files in the filesystem.
All of these inputs are converted to a class path called the _input class path_.

When the input is a Maven BOM, the elements in the BOM are
converted to a list of Maven coordinates.
If the BOM imports another BOM, the elements of the second BOM are recursively
added to the list of Maven coordinates. This list of Maven coordinates is handled
in the same way as a directly-provided list of coordinates.

When the input is a list of Maven coordinates, they are resolved to a list of jar files
that consists of the artifacts and their dependencies. This list of jar files is
handled in the same way as a directly-provided list of jar files.

When the input is a list of class directories and jar files,
they are used directly as the _input class path_.

### Output

The tool reports linkage errors for the input class path.
Each of the linkage errors contains information on the
source class and the destination class of the reference, and has one of three types:
_missing class_, _missing method_, or _missing field_.
     
### Class Reference Graph and Reachability

In order to provide a diagnosis in the output report, the tool builds a [class reference graph](
../library-best-practices/glossary.md#class-reference-graph),
and annotates linkage errors with [reachability](
../library-best-practices/glossary.md#reachability) from [entry point classes](
../library-best-practices/glossary.md#entry-point-class).

Entry point classes are different for the input of checks:
  - **Check for a Maven BOM**: classes in the Maven artifacts listed in the BOM
  - **Check for a list of Maven coordinates**: classes in the Maven artifacts
  - **Check for a list of class and jar files**: all classes in the input are entry points.
    This means that every linkage error is reachable.

### Exclusion Files

Users can specify an exclusion file to filter out known linkage errors.
For the file format, see [exclusion files](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/wiki/Linkage-Checker-Exclusion-File).

By default, Linkage Checker uses [linkage-checker-exclusion-default.xml](
https://github.com/GoogleCloudPlatform/cloud-opensource-java/blob/master/dependencies/src/main/resources/linkage-checker-exclusion-default.xml)
to filter the known linkage errors. When a user specifies an exclusion file, Linkage Checker applies
it in addition to this default exclusion file.