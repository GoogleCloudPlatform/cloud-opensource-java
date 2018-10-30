[JLBP-16] Produce a BOM for multi-module projects
-------------------------------------------------

### The BOM (Bill of Materials)

- A BOM is a `pom.xml` file that has a `<dependencyManagement>` section that
  lists all the modules of a project. It can omit the `<modules>` section, and
  it has no `<dependencies>` section. It also has `<packaging>pom</packaging>`.
  - Documentation is available at
    https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html
    .
  - All of the modules of the project should be included. Otherwise, a consumer
    who depends on an unspecified module must specify and upgrade the version of
    that module, defeating the purpose of the BOM.
- The purpose of a BOM is to enable consumers of a library to select a
  consistent set of versions for artifacts released by a single project. When
  imported, a BOM operates as a filter. It does not cause any modules to be
  added as dependencies. Instead, for any dependency in a project's dependency
  tree, *if* that module appears in the BOM, the version from the BOM is used.
- If all modules in a project have the same version, use that version for the
  BOM. Example: grpc-bom v1.15.0 would specify grpc-auth v1.15.0, gprc-core
  v1.15.0, etc.
- A BOM is not necessary for projects that only have one module, since there is
  no consistency problem in that case.
- See the details specific to each build system in the sections below.

### Libraries using Maven

- Create a module containing only the BOM `pom.xml` file and add the BOM module
  to the parent `<modules>` list.
- A BOM can inherit from a parent as long as the parent is not part of the build
  path.
  - Unlike the module POMs of a Maven project, the BOM does not inherit from the
    parent POM that's used for building other modules of the library.
  - The reason is that a parent will have direct (and possibly transitive)
    dependencies in its `<dependencyManagement>` section to ensure that its
    build is consistent, but these dependency versions shouldn't be imported by
    consumers who import the BOM.
- Example BOM:
  [google-cloud-bom](https://github.com/GoogleCloudPlatform/google-cloud-java/blob/master/google-cloud-bom/pom.xml).

### Libraries using Gradle

- A Gradle project can either maintain a pom.xml and release it using a
  specially configured module, or the project can generate the `pom.xml` file.
- Example for maintaining a `pom.xml` file: gax-java -
  [build.gradle](https://github.com/googleapis/gax-java/blob/master/gax-bom/build.gradle)
  and
  [pom.xml](https://github.com/googleapis/gax-java/blob/master/gax-bom/pom.xml).
- There is not yet an example of a project that generates a `pom.xml` file.
  - Note: This approach has a limitation that the BOM cannot include any
    classifiers in the dependency specifications.
