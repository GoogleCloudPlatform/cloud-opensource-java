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
- [google-http-java-client](https://github.com/googleapis/google-http-java-client)
- [google-cloud-java](https://github.com/googleapis/google-cloud-java)

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
        <version>3.0.0-M3</version>
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

You can use a BOM like this—this example is for `libraries-bom`:

```
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>libraries-bom</artifactId>
        <version>3.4.0</version>
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

In this example, since the BOM manages library versions, the
version of the google-cloud-storage artifact is omitted.

### Gradle

Gradle selects the highest version of any given dependency in the
dependency tree.

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
