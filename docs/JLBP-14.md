# [JLBP-14] Specify a single, overridable version of each dependency

Give the version of each dependency as a single value such as `2.3`.
Do not use a Maven range such as `[2.3,2.9]`, `[2.3,)`, or even `[2.3]`.
Do not use a Gradle dynamic version such as
`com.google.api-client:google-api-client:+`.

When a build system specifies a range instead of a
single version for a dependency, builds at different points
in time can see different versions of that dependency.
This causes builds to be non-reproducible and can break your product unexpectedly. Builds of the same code with the same compiler at different
times can produce different artifacts with different behavior.

Version ranges can pull in incomplete releases that break the
build. For example, a [version range in appengine-gcs-client's
dependency on google-http-java-client broke multiple customers'
builds](https://github.com/GoogleCloudPlatform/appengine-gcs-client/issues/71)
when only half the artifacts in the project were pushed one afternoon.

Version ranges are a security hole. A project that uses a range to select
the version of a dependency picks up new versions of that dependency when
they're pushed to Maven Central. Someone with release privileges can take
advantage of this to slip malicious code into projects without proper review. A
variant of this [attack in the node.js ecosystem was used to steal
Bitcoins](https://www.theregister.co.uk/2018/11/26/npm_repo_bitcoin_stealer/).

Version ranges have another, less obvious problem. A normal soft version
requirement such as `<version>2.3</version>` says that Maven prefers
version 2.3 but will accept version 2.0 or 3.0 if other dependencies
pick that first or require it. This flexibility is important
for large projects with many dependencies, since it's very rare for
all artifacts that depend on a particular library to agree on which version they
need.

A version range does not have this flexibility. When Maven sees
`<version>[2.3,2.9]</version>`, it accepts any version between 2.3 and 2.9 inclusive. It does not accept 2.0 or 3.1.5.
A single version range such as `<version>[2.3]</version>` is even stricter,
requiring version 2.3 and no other. If two `dependency` elements in the
graph have non-overlapping version ranges, the build fails.

Some multimodule  projects such as gRPC specify single element version ranges
to cause the build to fail early when multiple versions of *intraproject*
dependencies are mixed. For example, 
`io.grpc:grpc-alts:1.25.0` depends on exactly `io.grpc:grpc-core:1.25.0`:

```
<dependency>
  <groupId>io.grpc</groupId>
  <artifactId>grpc-core</artifactId>
  <version>[1.25.0]</version>
</dependency>
```

`io.grpc:grpc-alts:1.25.0` and `io.grpc:grpc-core:1.23.0` cannot appear in 
the classpath at the same time. If dependency mediation selects both
`io.grpc:grpc-alts:1.25.0` and `io.grpc:grpc-core:1.23.0`, the build fails, and the developer must fix the conflict before they can proceed. This still
adds friction for developers who depend on multiple versions of
different modules, especially if they only depend on the different 
versions through several layers of transitive dependencies. It can be 
necessary when different modules are tightly coupled to each other,
but it is not ideal.

Rapidly changing, pre-1.0 projects with unstable APIs have a greater 
need for hard requirements such as `<version>[0.24.0]</version>` 
to ensure their modules all work together. 
OpenCensus did not do this, and as a result there have been several problems
when different versions of different OpenCensus artifacts that 
did not work with each other appeared in other projects' 
classpath at the same time. In the best case, hard requirements are
a stopgap measure that can be discarded once the API stabilizes.

Even if all hard requirements can be satisfied, version ranges override
the normal Maven algorithm for selecting among different versions of the
same artifact. They pin the dependency tree to that version or versions,
even when other artifacts in the classpath specify newer compatible versions
that include important bug and security fixes.

See [Dependency Version Requirement Specification](https://maven.apache.org/pom.html#Dependency_Version_Requirement_Specification)
for more information.
