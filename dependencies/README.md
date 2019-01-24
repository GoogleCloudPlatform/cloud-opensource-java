# Static Linkage Checker

Static Linkage Checker is a tool that finds [static linkage errors](
../library-best-practices/glossary.md#static-linkage-error)
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

- **For organizations** that provide multiple libraries developed by different teams,
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

The input of the tool is either the Maven coordinates of a BOM, 
a list of Maven coordinates, or a list of class and jar files in the filesystem.
All of these inputs are converted to a class path for the static linkage check,
which is the _input class path_.

When the input is a Maven BOM, the elements in the BOM are
converted to a list of Maven coordinates.
If the BOM imports another BOM, the elements of the second BOM are recursively
added to the list of Maven coordinates. This list of Maven coordinates is handled
in the same way as a directly-provided list of coordinates (see below).

When the input is a list of Maven coordinates, they are resolved to a list of jar files
that consists of the artifacts and their dependencies, through a
[Maven dependency graph](#maven_dependency_graph).
This list of jar files is handled in the same way as a directly-provided list of jar files
(see below).

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

<a name="maven_dependency_graph"></a>
## Maven Dependency Graph

A Maven dependency graph is a graph data structure where
- Node: a node is a Maven artifact identified as `groupId:artifactId[:classifier]:version`, where
  `classifier` is optional.
- Edge: an (directed) edge is a dependency between Maven artifacts. A dependency from a Maven
  artifact (_source_ of the dependency) to another artifact (_target_ of the dependency) is defined
  in the `dependencies` element in pom.xml of a Maven artifact.
  
  A dependency has a boolean attribute `optional` and a string attribute `scope`,
  among other properties listed in [POM Reference: Dependencies][1].

  Self-loops or parallel edges are not possible.

### Graph Construction

Given a ordered list of Maven artifacts, a Maven dependency graph is constructed in the
following manner:

- 1. Start with a graph with nodes of the Maven artifacts in the list.
     The nodes are called _initial nodes_.
- 2. Pick up a node in a graph in breadth-first manner.
- 3. By reading dependencies of the node, add new nodes corresponding to the target Maven artifacts,
     identified by `groupId:artifactId[:classifier]:version`, if not present.
- 4. Add edges from the source node to the target nodes of the Maven artifacts.
- 3. Repeat step 2-4, until all nodes are visited in this breadth-first traversal.

A graph construction may _fail_ when there is a problem in constructing a graph (see below).

### Edge Cases

#### Cyclic Dependency

Cyclic dependency may exist in a Maven dependency graph. It does not become a cause of a graph
construction failure.

#### Unavailable Artifact

A Maven artifact may be unavailable through Maven repositories, making it impossible to complete
a graph construction.

An unavailability of Maven artifact is called _safe_ when the path from the initial nodes to
the missing artifact contains `optional` or `scope: provided` dependency. An edge whose source
artifact is missing but is safe, is skipped in a graph construction (step 4).

When there is an unavailable Maven artifact and it is not safe, the graph construction fails.

#### Unsatisfied Version Constraints

A dependency element in pom.xml may have a [version range specification][2].
Each of the specifications creates a version constraint.

When there is a version constraint that cannot be satisfied during graph construction,
the graph construction fails.

### Class Path Generation through Maven Dependency Graph

A class path (list of jar files of Maven artifacts) can be generated from a Maven dependency graph.
A class path is built by picking up Maven artifacts in breadth-first manner,
starting from the first node of the initial nodes of a Maven dependency graph.

During the pick-up, duplicate artifacts identified by
`groupId:artifactId[:classifier]:version` are discarded.

When there are multiple versions of a Maven artifact
identified by `groupId:artifactId[:classifier]` (without version), a version is picked up
using one of following strategies:

- **Maven dependency mediation strategy**: the version of the Maven artifact closest to the initial
  nodes is selected.
- **Gradle dependency mediation strategy**: the highest version among a Maven dependency graph is
  selected.


[1]: https://maven.apache.org/pom.html#Dependencies
[2]: https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification
