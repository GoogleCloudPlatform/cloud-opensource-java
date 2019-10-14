# [JLBP-15] Publish a BOM for multi-module projects

A [BOM](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
enables consumers of a library to select
consistent versions for artifacts included in that library. Importing a
BOM does not add any dependencies to a project. Instead, for any module
in a project's dependency tree, *if* that module appears in the BOM,
the version from the BOM is used.

## The BOM (Bill of Materials)

A BOM is a `pom.xml` file that has `<packaging>pom</packaging>`.
The `<dependencyManagement>` section lists all the modules of a project.
The BOM can omit the `<modules>` section and does not have a `<dependencies>`
section.
Consumers of a multi-module library import the library's BOM
in their own `<dependencyManagement>` section and omit the
versions from the specific modules they import. The versions will
be set by the BOM. This provides consistent versions for artifacts released
by a single project.
Your project's BOM should include every non-test-scoped module in the project.
Otherwise, a consumer who depends on an unspecified module must specify and
upgrade the version of that module, defeating the purpose of the BOM.

A BOM is not necessary for projects that only have one module, since there is
no consistency problem in that case.

See the details specific to each build system in the following sections.

## Libraries built with Maven

Create a module containing only the BOM `pom.xml` file and add the BOM module
to the parent `<modules>` list.
A BOM can inherit from a parent as long as the parent is not part of the build
path. Unlike the module POMs of a Maven project, the BOM does not inherit from the
parent POM that's used for building other modules of the library.
The reason is that a parent will have direct (and possibly transitive)
dependencies in its `<dependencyManagement>` section to ensure that its
build is consistent, but these dependency versions shouldn't be imported by
consumers who import the BOM.

Example BOM: [google-cloud-bom](https://github.com/GoogleCloudPlatform/google-cloud-java/blob/master/google-cloud-bom/pom.xml).

## Libraries built with Gradle

A Gradle project should maintain a pom.xml and release it using a specially
configured module. gax-java does this, for example,
in its [gax-bom](https://github.com/googleapis/gax-java/tree/master/gax-bom)
module.
