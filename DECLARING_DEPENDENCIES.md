# Declaring Dependencies on Google Java Libraries

Google maintains a number of open source Java libraries that make it
easier to use services in Google Cloud Platform (GCP). Additionally
Google maintains several foundational libraries that can be used for
very general purposes, which the GCP libraries also depend on. This
document explains how to use bills of materials (BOMs) and other
strategies to avoid dependency conflicts in these libraries.

These recommendations apply to the following libraries:

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

To ensure that your own project uses compatible versions of these
libraries, follow the guidance below for your build system.

### Maven

#### Use the requireUpperBoundDeps enforcer rule

Maven's dependency mediation algorithm can select older versions
instead of newer versions, meaning new features will be missing that
other libraries depend on. The `requireUpperBoundDeps` enforcer rule
can be used to automatically discover incorrect version selection
because it fails the build when an older version is chosen instead of
a newer one.

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
that library to versions from different releases. The `libraries-bom`
dictates consistent versions for all GCP orbit artifacts.

You use the BOM like this:

```
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>libraries-bom</artifactId>
        <version>2.5.0</version>
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
    <dependency>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
    </dependency>
  </dependencies>
```

In this example, since the BOM manages library versions, the
version of google-cloud-storage and guava are omitted.

See the "Choosing BOMs" section below if you need to import more than one BOM.

### Gradle

Gradle selects the highest version of any given dependency in the
dependency tree.

#### Import BOMs to ensure your dependencies are consistent

When a library publishes multiple artifacts, using different versions
for different artifacts may cause dependency conflicts. To fix this
problem, you can import a BOM to force consistent versions, as long as
you are using at least Gradle 4.6. To do this:

- Turn on BOM support:
  - If you are using Gradle 4.x:
    - Add the following to your `settings.gradle`: `enableFeaturePreview('IMPROVED_POM_SUPPORT')`
  - If you are using Gradle 5.x or higher, BOM support is on by default.
- Add a dependency on the BOM for the library you depend on
- Remove the version from the dependency declarations of the artifacts in that library

For an example, see [gax-java#690](https://github.com/googleapis/gax-java/pull/690/files).

See the "Choosing BOMs" section below if you need to import more than one BOM.

## Choosing BOMs

By default, import the highest-level BOM that your project
depends on. For example, if you depend on both `grpc-java` and
`google-cloud-java`, then use `google-cloud-bom` (since `google-cloud-java`
depends on `grpc-java`). This generally provides you the versions of everything
you need, because the transitive dependencies of that library should
all be compatible as well.

However, if your project independently pulls in transitive
dependencies at different versions, you may also need to specify
lower-level BOMs to ensure compatibility, since each BOM only controls
the versions of the library that generates it. Since the GCP Java
libraries follow [semver](https://semver.org/), you should be able to pick the highest
version in your dependency tree for any particular library, as long as
your dependency tree agrees on the same major version. Here is the
process to follow for each library that has BOM support:

1. Identify the highest version seen for any artifact produced by that library
  a. For example, if you see `com.google.api:gax:1.34.0` and
     `com.google.api:gax-grpc:1.42.0`, then use version 1.42.0
2. Import the BOM corresponding to that library using the highest version you see
  a. For the example above, use `com.google.api:gax-bom:1.42.0`
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
that cannot be resolved by following the recommendations of this
document. Such conflicts are called *intrinsic conflicts*. There is an
ongoing effort to remove intrinsic conflicts among GCP open source
Java libraries and prevent new ones from occurring.
The [Cloud Open Source Java Dashboard](
https://storage.googleapis.com/cloud-opensource-java-dashboard/com.google.cloud/libraries-bom/snapshot/index.html)
reports the current results of compatibility checks.
As of the time of this writing, some conflicts are still in the
process of being fixed, but they should not be encountered by most
users who only use the public APIs of the libraries. If you encounter
such a conflict, please
[file an issue against cloud-opensource-java](https://github.com/GoogleCloudPlatform/cloud-opensource-java/issues/new).

## Background details about library compatibility

The following is true for the libraries in the GCP open source Java
library ecosystem:

- For each library release, the artifacts included in that release are
  compatible with each other.
- Each library publishes a BOM that defines the
  compatible versions for each release.
- Each library follows semantic versioning. This means that once a
  library reaches a 1.x version, features and APIs can be added in minor/patch
  releases but not removed within a major version.

This combination of characteristics means that you can generally avoid
dependency conflicts by doing the following:

1. Use the highest version of each dependency
2. Import a BOM for each library whose artifacts need to use a
  consistent version.
