# Declaring Dependencies on Google Java Libraries

Google maintains a number of open source Java libraries that make it
easier to use services in Google Cloud Platform (GCP). Additionally Google
maintains several foundational libraries that can be used for very
general purposes, which the GCP libraries also depend on. A user of
these libraries needs to use compatible versions of them in order to
avoid dependency conflicts. This document explains how to use BOMs
(bill of materials) to accomplish that and explains additional
practices that make dependency management go more smoothly.

These recommendations apply to the usage of the following libraries:

- [guava](https://github.com/google/guava)
- [protobuf](https://github.com/protocolbuffers/protobuf)
- [grpc-java](https://github.com/grpc/grpc-java)
- [gax-java](https://github.com/googleapis/gax-java)
- [google-http-java-client](https://github.com/googleapis/google-http-java-client)
- [google-oauth-java-client](https://github.com/googleapis/google-oauth-java-client)
- [google-api-java-client](https://github.com/googleapis/google-api-java-client)
- [google-cloud-java](https://github.com/googleapis/google-cloud-java)
- [beam](https://github.com/apache/beam)

## Ensuring Compatibility

If you depend on one or more of these libraries, in order to
ensure that your own project uses compatible versions of them, follow
the guidance below for your build system.

### Maven

#### Use the requireUpperBoundDeps enforcer rule

Dependency conflicts can happen with Maven even when libraries follow
semver because Maven's dependency mediation algorithm can select older
versions instead of newer versions, meaning new features will be
missing that other libraries depend on. The `requireUpperBoundDeps`
enforcer rule can be used to automatically discover incorrect version
selection because it fails the build if anything other than the most
recent version is chosen.

You can add `requireUpperBoundDeps` to your build like this:

```
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
        <version>3.0.0-M2</version>
        <executions>
          <execution>
            <id>enforce</id>
            <goals>
              <goal>enforce</goal>
            </goals>
            <configuration>
              <rules>
                <requireUpperBoundDeps/>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
```

When the newest versions are not compatible, you can use BOMs to
select compatible versions.

#### Import BOMs to ensure dependencies are consistent

Conflicts can occur when multiple artifacts from a single library are
part of a dependency tree, and Maven resolves different artifacts from
that library to versions from different releases. Using a BOM fixes
this problem because a BOM dictates consistent versions for all
artifacts from a library.

You can use a BOM like this - this example is for `google-cloud-bom`:

```
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-bom</artifactId>
        <version>0.81.0-alpha</version>
        <type>pom</type>
        <scope>import</scope>
       </dependency>
     </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>com.google.cloud</groupId>
      <artifactId>google-cloud-storage</artifactId>
    </dependency>
  </dependencies>
```

In the example above, since the BOM manages library versions, the
version of the google-cloud-storage artifact is omitted.

See the "Choosing BOMs" section below if you need to import more than one BOM.

### Gradle

Gradle automatically selects the highest version in the dependency
tree of any given dependency.

#### Import BOMs to ensure your dependencies are consistent

It is possible to accidentally use versions of different artifacts
from a library from different releases and experience a dependency
conflict. To fix this problem, you can import a BOM in Gradle to force
consistent versions, as long as you are using at least Gradle 4.6.

In order to make use of a BOM, do the following:

- Gradle 4.x (4.6+):
  - Add the following to your `settings.gradle`: `enableFeaturePreview('IMPROVED_POM_SUPPORT')`
- Add a dependency on the BOM for the library you depend on
- Remove the version from the dependency declarations of the artifacts in that library

For an example, see [gax-java#690](https://github.com/googleapis/gax-java/pull/690/files).

See the "Choosing BOMs" section below if you need to import more than one BOM.

## Choosing BOMs

Generally, you should import the highest-level BOM that your project
depends on. For example, if you depend on both `grpc-java` and
`google-cloud-java`, then use `google-cloud-bom` (since `google-cloud-java`
depends on `grpc-java`). This should get you the versions of everything
you need, because the transitive dependencies of that library should
all be compatible as well.

However, if your project independently pulls in transitive
dependencies at different versions, you may also need to specify
lower-level BOMs to ensure compatibility, since each BOM only controls
the versions of the library that generates it. Since the GCP Java
libraries follow semver, you should be able to pick the highest
version in your dependency tree for any particular library. Here is
the process to follow for each library that has BOM support:

1. Identify the highest version seen for any artifact produced by that library
  a. For example, if you see `gax:1.34.0` and `gax-grpc:1.42.0`, then use version 1.42.0
2. Import the BOM corresponding to that library using the highest version you see
  a. For the example above, use `gax-bom:1.42.0`
3. Repeat as long as there are other libraries with BOM support where
   you see multiple versions of the library's artifacts

Note that there will not necessarily be a BOM available at the version
you need. See the BOM reference section below for the first
available version of each BOM.

## BOM reference

None of the GCP open source Java libraries were initially released
with BOMs - they were added later to provide better consistency for
users. The following table shows the first version that each library
published a BOM for, and the BOM artifact name.

| library | BOM artifact | First available version |
| --- | --- | --- |
| guava | com.google.guava:guava-bom | 27.1-jre/27.1-android (08-Mar-2019) |
| protobuf | com.google.protobuf:protobuf-bom | 3.7.0 (06-Mar-2019) |
| grpc-java | io.grpc:grpc-bom | 1.19.0 (27-Feb-2019) |
| gax-java | com.google.api:gax-bom | 1.34.0 (19-Oct-2018) |
| google-http-java-client | com.google.http-client:google-http-client-bom | 1.27.0 (09-Nov-2018) |
| google-oauth-java-client | com.google.oauth-client:google-oauth-client-bom | 1.27.0 (10-Nov-2018) |
| google-api-java-client | com.google.api-client:google-api-client-bom | 1.27.0 (12-Nov-2018) |
| google-cloud-java | com.google.cloud:google-cloud-bom | 0.32.0-alpha (11-Dec-2017)  |
| beam | org.apache.beam:beam-sdks-java-bom | 2.10.0 (06-Feb-2019) |

## Intrinsic conflicts

It is possible for GCP open source Java libraries to have conflicts
with each other that cannot be resolved when the user follows the
recommendations of this document. Such conflicts are called intrinsic
conflicts. There is an ongoing effort to ensure that intrinsic
conflicts are avoided among Google open source Java libraries. A
dashboard that reports the current results of compatibility checks is
accessible from the [Cloud Open Source Java Dashboard](https://storage.googleapis.com/cloud-opensource-java-dashboard/dashboard/target/dashboard/dashboard.html).
As of the time of this writing, there are still some conflicts that
are in the process of being fixed, but they should not be encountered
by most users who only use the public APIs of the libraries. If you
encounter such a conflict, please [file an issue against cloud-opensource-java](https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/new).

## Background details about library compatibility

The following is true for the libraries in the GCP open source Java
library ecosystem:

- For each library release, the artifacts included in that release are
  compatible with each other.
- Each library publishes a BOM that defines the
  compatible versions for each release.
- Each library follows semantic versioning. This means that once a
  library reaches a 1.x version, features can be added in minor/patch
  releases but not removed within a major version.

This combination of characteristics means that you can generally avoid
dependency conflicts by doing the following:

1. Using the highest version of each dependency
2. Importing a BOM for each library whose artifacts need to use a
  consistent version.
