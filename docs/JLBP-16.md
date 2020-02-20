# [JLBP-16] Ensure upper version alignment of dependencies

For multi-module projects, this best practice assumes you have already applied
[JLBP-15](JLBP-15.md), so that your project has a BOM.

Upper version alignment means that when more than one version of a dependency
is present in a module's dependency tree, the build system selects
the highest version. Upper version alignment ensures that when packages are
upgraded to higher versions which don’t make any breaking changes,
there are no new linkage conflicts.

## Achieving upper version alignment

Upper version alignment increases the likelihood that consumers' build systems
select the right versions of direct and transitive dependencies, reducing the
number of conflicts.

Gradle always chooses the highest version it finds in the dependency graph,
regardless of where it occurs. Maven, however, selects
the version it encounters first when traversing the dependency graph
in level order, regardless of version. 

To fix a version misalignment caused by a shorter
path to a lower version of a dependency, you can either:

1. Update the dependency on the lower version to the upper bound.
   This usually requires submitting a patch to another project 
   and waiting for it to release. 
   
2. Add the newer version of the dependency to the `dependencyManagement` 
   section of your project, even though it does not directly 
   import any of that dependency's classes. 

The `requireUpperBoundDeps` enforcer rule verifies that
a project uses the highest version of each dependency in your dependency tree.

To ensure that dependencies between modules in the project are consistent,
the parent POM should import the library's own BOM into its
`<dependencyManagement>` section.

  - [Example import in google-cloud-java](https://github.com/GoogleCloudPlatform/google-cloud-java/blob/36409f5b1df89609eaef92d09cebea97931339bd/google-cloud-clients/pom.xml#L174).

To ensure that usage of dependencies is consistent, for any direct
dependencies that have a BOM of their own, import those BOMs in a
`<dependencyManagement>` section.

  - If multiple imported BOMs manage the same dependency, use the
    `<dependencyManagement>` of the parent POM to select a compatible version,
    typically the highest.
  - Order doesn't matter for the override, because all explicit version
    declarations in dependencyManagement take precedence over all BOM
    imports. (Order between BOMs matters - the earliest import of a dependency
    takes precedence over later imports of the same dependency.)

For direct dependencies that don't have a BOM (for example, Joda-Time), make
sure only one pom.xml defines the version, so that the library doesn't
accidentally depend on different versions in different modules.

  - The first option is to specify dependency versions in the
    `<dependencyManagement>` section of the parent POM. Use this option by
    default because it is the most structured.

    When declaring a dependency anywhere in the project, omit the version
    number so that the version from the parent's `<dependencyManagement>`
    section takes effect.

  - The second option is to use version properties in the parent that are
    referenced by child modules.

    When declaring a dependency anywhere in the project, use the Maven
    property declared in the parent.

For any transitive dependency that fails a `requireUpperBoundDeps` check, add
the dependency as a direct dependency so that the path to the correct version
is shorter, leading Maven to select it instead of the wrong version.

Have each module POM inherit from the parent POM of the library so that the
parent's `<dependencyManagement>` section is used.
